# terraform/main.tf — инфраструктура ASG в Yandex Cloud
# Создаёт:
#   - Managed Kubernetes cluster (yandex_kubernetes_cluster)
#   - Node group (3 узла e2-medium)
#   - Container Registry
#   - Managed PostgreSQL cluster
#   - Managed Redis cluster
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = ">= 0.110.0"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.5.0"
    }
  }

  # Backend — Object Storage (S3-совместимый в Yandex Cloud).
  # Параметры задаются через переменные окружения (TF_VAR_*) или terraform init -backend-config.
}

# ─── Провайдер Yandex Cloud ──────────────────────────────────────────────
provider "yandex" {
  cloud_id  = var.cloud_id
  folder_id = var.folder_id
  zone      = var.default_zone
  # Сервисный аккаунт для Terraform (с ролями editor/admin/k8s.admin/registry.admin)
  service_account_key_file = var.yc_service_account_key_file
}

# ─── Локальные переменные ────────────────────────────────────────────────
locals {
  # Список подсетей для кластера (берётся из data-источников ниже)
  k8s_subnet_ids = data.yandex_vpc_subnet.asg_subnets[*].id
  # Метки ресурсов
  common_labels = {
    project     = "asg"
    environment = var.environment
    managed_by  = "terraform"
  }
}

# ─── Data: существующие подсети ──────────────────────────────────────────
# Подсети должны быть созданы заранее (или импортированы) — Terraform их не создаёт,
# чтобы не задваивать управление сетью с другими стеками.
data "yandex_vpc_subnet" "asg_subnets" {
  count     = length(var.subnet_ids)
  subnet_id = var.subnet_ids[count.index]
}

# ─── Container Registry ──────────────────────────────────────────────────
# Хранилище образов. Альтернатива: ghcr.io — тогда registry не создаётся здесь.
resource "yandex_container_registry" "asg" {
  name      = "asg-registry"
  folder_id = var.folder_id
  labels    = merge(local.common_labels, {
    # Иммутабельные теги прод-образов (запрет перезаписи tag=prod)
    immutable = var.environment == "prod" ? "true" : "false"
  })
}

# ─── IP для ingress-контроллера (позже пробрасывается на nginx-ingress) ───
resource "yandex_vpc_address" "asg_ingress_ip" {
  name = "asg-ingress-ip"
  external_ipv4_address {
    zone_id = var.default_zone
  }
}

# KMS-ключ для шифрования секретов etcd в кластере K8s
resource "yandex_kms_symmetric_key" "asg_secrets" {
  name            = "asg-kms-secrets"
  description     = "KMS-ключ для шифрования секретов etcd ASG"
  rotation_period = "8760h"   # 1 год
  labels          = local.common_labels
}

# ─── Managed Service for Kubernetes ──────────────────────────────────────
# Создаёт managed k8s кластер в указанной VPC и подсетях.
resource "yandex_kubernetes_cluster" "asg" {
  name       = "asg-cluster"
  network_id = var.network_id
  folder_id  = var.folder_id
  labels     = local.common_labels

  # Мастер-узел: зональный (одна зона — дешевле; для prod — региональный)
  master {
    version = var.k8s_version

    # Зональный мастер (single-AZ). Для prod заменить на regional { region = "ru-central1" }.
    zonal {
      zone      = var.default_zone
      subnet_id = local.k8s_subnet_ids[0]
    }

    # Публичный IP для API-сервера (на prod — закрыть security groups)
    public_ip = true

    maintenance_policy {
      auto_upgrade = true
      maintenance_window {
        start_time = "02:00"
        duration   = "3h"
      }
    }

    security_group_ids = var.k8s_master_security_group_ids
  }

  # Сервисный аккаунт для управления кластером (с ролью k8s.admin/k8s.clusters.agent)
  service_account_id = var.k8s_service_account_id

  # Сервисный аккаунт для node group (с ролями container-registry.images.puller,
  # load-balancer.client и vpc.publicAdmin для подсетей)
  node_service_account_id = var.k8s_node_service_account_id

  # Версия — выпуски YC следуют за upstream K8s
  release_channel = var.k8s_release_channel

  network_policy_provider = "CALICO"

  # Шифрование секретов в etcd через KMS
  kms_provider {
    key_id = yandex_kms_symmetric_key.asg_secrets.id
  }

  depends_on = [
    yandex_container_registry.asg,
    yandex_kms_symmetric_key.asg_secrets
  ]
}

# ─── Node Group: 3 узла e2-medium (2 vCPU, 4 GiB RAM каждый) ────────────
resource "yandex_kubernetes_node_group" "asg_workers" {
  name       = "asg-workers"
  cluster_id = yandex_kubernetes_cluster.asg.id
  version    = var.k8s_version
  labels     = local.common_labels

  instance_template {
    platform_id = "standard-v2"
    name        = "asg-worker-{instance.short_id}"

    resources {
      cores         = var.worker_cores
      memory        = var.worker_memory
      core_fraction = 100
    }

    boot_disk {
      type = "network-ssd"
      size = var.worker_disk_size
    }

    # Containerd — рекомендуется для новых кластеров
    container_runtime {
      type = "containerd"
    }

    network_interface {
      ipv4               = true
      ipv6               = false
      nat                = true
      subnet_ids         = local.k8s_subnet_ids
      security_group_ids = var.k8s_node_security_group_ids
    }

    scheduling_policy {
      preemptible = false
    }

    # metadata для cloud-init (SSH-ключ администратора).
    # В metadata нельзя передавать user-data, если задан ssh-keys — нужно выбрать одно.
    metadata = {
      ssh-keys = "${var.admin_username}:${file(var.ssh_public_key_path)}"
    }
  }

  scale_policy {
    fixed_scale {
      size = var.worker_count
    }
  }

  allocation_policy {
    dynamic "location" {
      for_each = toset(data.yandex_vpc_subnet.asg_subnets[*].zone)
      content {
        zone = location.value
      }
    }
  }

  maintenance_policy {
    auto_upgrade = true
    auto_repair  = true
    maintenance_window {
      start_time = "03:00"
      duration   = "3h"
    }
  }

  depends_on = [
    yandex_kubernetes_cluster.asg
  ]
}

# ─── Managed PostgreSQL ──────────────────────────────────────────────────
# Кластер: 1 хост (ha=false) или 2 хоста в зонах (ha=true).
# Превращается в managed-сервис: автоматические бэкапы, PITR, мониторинг.
resource "yandex_mdb_postgresql_cluster" "asg" {
  name        = "asg-postgres"
  environment = var.environment
  network_id  = var.network_id
  folder_id   = var.folder_id
  labels      = local.common_labels

  config {
    version = var.postgres_version

    resources {
      resource_preset_id = var.postgres_resource_preset
      disk_type_id       = "network-ssd"
      disk_size          = var.postgres_disk_size
    }

    # Окно резервного копирования — ежедневно в 02:00 UTC
    backup_window_start {
      hours   = 2
      minutes = 0
    }

    # Тонкие параметры PostgreSQL (tuning)
    postgresql_config = {
      max_connections               = 200
      shared_buffers                = 1024    # MB
      work_mem                      = 32      # MB
      maintenance_work_mem           = 256    # MB
      effective_cache_size          = 2048   # MB
      log_min_duration_statement    = 500    # лог медленных запросов >500ms
      autovacuum                    = true
      autovacuum_max_workers         = 5
      timezone                      = "Europe/Moscow"
    }
  }

  # Хост в основной зоне
  host {
    zone             = var.default_zone
    subnet_id        = local.k8s_subnet_ids[0]
    assign_public_ip = false
  }

  # HA: второй хост во вторичной зоне (если postgres_ha=true)
  dynamic "host" {
    for_each = var.postgres_ha ? [1] : []
    content {
      zone             = var.secondary_zone
      subnet_id        = length(local.k8s_subnet_ids) > 1 ? local.k8s_subnet_ids[1] : local.k8s_subnet_ids[0]
      assign_public_ip = false
    }
  }

  maintenance_window {
    type = "WEEKLY"
    day  = "SAT"
    hour = 3
  }
}

# База данных и пользователь ASG
resource "yandex_mdb_postgresql_database" "asg" {
  cluster_id = yandex_mdb_postgresql_cluster.asg.id
  name       = "asg"
  owner      = "asg"
  lc_collate = "C.UTF-8"
  lc_type    = "C.UTF-8"
}

resource "yandex_mdb_postgresql_user" "asg" {
  cluster_id = yandex_mdb_postgresql_cluster.asg.id
  name       = "asg"
  password   = var.postgres_password
  grants     = ["asg"]
}

# ─── Managed Redis ────────────────────────────────────────────────────────
# HA Redis: 3 хоста (1 master + 2 replica).
resource "yandex_mdb_redis_cluster" "asg" {
  name             = "asg-redis"
  environment      = var.environment
  network_id       = var.network_id
  folder_id        = var.folder_id
  labels           = local.common_labels
  persistence_mode = "ON"
  tls_enabled      = var.environment == "prod"

  config {
    version          = var.redis_version
    maxmemory_policy = "LRU"
    timeout          = 300
    password         = var.redis_password
  }

  resources {
    resource_preset_id = var.redis_resource_preset
    disk_type_id       = "network-ssd"
    disk_size          = var.redis_disk_size
  }

  host {
    zone      = var.default_zone
    subnet_id = local.k8s_subnet_ids[0]
  }

  host {
    zone      = var.secondary_zone
    subnet_id = length(local.k8s_subnet_ids) > 1 ? local.k8s_subnet_ids[1] : local.k8s_subnet_ids[0]
  }

  host {
    zone      = var.tertiary_zone
    subnet_id = length(local.k8s_subnet_ids) > 2 ? local.k8s_subnet_ids[2] : local.k8s_subnet_ids[0]
  }
}
