# terraform/variables.tf — переменные инфраструктуры ASG
# ─────────────────────────────────────────────────────────────────────────────

# ─── Идентификаторы Yandex Cloud (обязательные) ──────────────────────────
variable "cloud_id" {
  type        = string
  description = "Идентификатор облака Yandex Cloud (yc config get cloud-id)."
}

variable "folder_id" {
  type        = string
  description = "Идентификатор каталога Yandex Cloud (yc config get folder-id)."
}

variable "network_id" {
  type        = string
  description = "Идентификатор VPC-сети, в которой разворачивается ASG."
}

variable "subnet_ids" {
  type        = list(string)
  description = "Список ID подсетей для кластера K8s и БД. Минимум 1 (для non-HA), для HA — 3 в разных зонах."
  validation {
    condition     = length(var.subnet_ids) >= 1
    error_message = "Должна быть указана хотя бы одна подсеть."
  }
}

# ─── Аутентификация сервисного аккаунта Terraform ─────────────────────────
variable "yc_service_account_key_file" {
  type        = string
  description = "Путь к JSON-ключу сервисного аккаунта, под которым работает Terraform."
  default     = ""
}

# ─── Зоны доступности ─────────────────────────────────────────────────────
variable "default_zone" {
  type        = string
  default     = "ru-central1-a"
  description = "Основная зона доступности (A)."
}

variable "secondary_zone" {
  type        = string
  default     = "ru-central1-b"
  description = "Вторая зона доступности (B) — для HA-конфигураций."
}

variable "tertiary_zone" {
  type        = string
  default     = "ru-central1-d"
  description = "Третья зона доступности (D) — для HA Redis (3 хоста)."
}

# ─── Окружение ────────────────────────────────────────────────────────────
variable "environment" {
  type        = string
  default     = "prod"
  description = "Окружение: dev | staging | prod. Влияет на immutability registry, TLS Redis, etc."
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment должен быть dev, staging или prod."
  }
}

# ─── Kubernetes ──────────────────────────────────────────────────────────
variable "k8s_version" {
  type        = string
  default     = "1.29"
  description = "Версия Kubernetes в формате X.Y (Yandex Cloud поддерживает 1.27+)."
}

variable "k8s_release_channel" {
  type        = string
  default     = "REGULAR"
  description = "Канал обновлений: RAPID | REGULAR | STABLE."
  validation {
    condition     = contains(["RAPID", "REGULAR", "STABLE"], var.k8s_release_channel)
    error_message = "k8s_release_channel должен быть RAPID, REGULAR или STABLE."
  }
}

variable "k8s_service_account_id" {
  type        = string
  description = "ID сервисного аккаунта для управления кластером K8s (роль k8s.admin/k8s.clusters.agent)."
}

variable "k8s_node_service_account_id" {
  type        = string
  description = "ID сервисного аккаунта для node group (роли container-registry.images.puller, vpc.publicAdmin, load-balancer.client)."
}

variable "k8s_master_security_group_ids" {
  type        = list(string)
  default     = []
  description = "Список security group IDs для master-узла K8s."
}

variable "k8s_node_security_group_ids" {
  type        = list(string)
  default     = []
  description = "Список security group IDs для worker-узлов K8s."
}

# ─── Worker-узлы ─────────────────────────────────────────────────────────
variable "worker_count" {
  type        = number
  default     = 3
  description = "Число worker-узлов в группе (соответствует replicas в deployment)."
  validation {
    condition     = var.worker_count >= 1 && var.worker_count <= 100
    error_message = "worker_count должен быть от 1 до 100."
  }
}

variable "worker_cores" {
  type        = number
  default     = 2
  description = "Число vCPU на worker-узле (e2-medium = 2)."
}

variable "worker_memory" {
  type        = number
  default     = 4
  description = "Объём RAM в GiB на worker-узле (e2-medium = 4)."
}

variable "worker_disk_size" {
  type        = number
  default     = 100
  description = "Размер boot-диска worker-узла в GiB."
}

# ─── SSH-доступ к узлам ──────────────────────────────────────────────────
variable "ssh_public_key_path" {
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
  description = "Путь к публичному SSH-ключу для cloud-init на worker-узлах."
}

variable "admin_username" {
  type        = string
  default     = "admin"
  description = "Имя администратора для cloud-init."
}

# ─── Managed PostgreSQL ──────────────────────────────────────────────────
variable "postgres_version" {
  type        = string
  default     = "16"
  description = "Версия PostgreSQL (YC поддерживает 13, 14, 15, 16)."
}

variable "postgres_resource_preset" {
  type        = string
  default     = "s2.micro"
  description = "Пресет ресурсов PostgreSQL (s2.micro = 2 vCPU, 4 GiB)."
}

variable "postgres_disk_size" {
  type        = number
  default     = 50
  description = "Размер диска PostgreSQL в GiB."
  validation {
    condition     = var.postgres_disk_size >= 10
    error_message = "Диск PostgreSQL не может быть меньше 10 GiB."
  }
}

variable "postgres_ha" {
  type        = bool
  default     = true
  description = "Создать HA-кластер PostgreSQL (2 хоста в разных зонах)."
}

variable "postgres_password" {
  type        = string
  description = "Пароль пользователя asg для PostgreSQL. Сensitive."
  sensitive   = true
}

# ─── Managed Redis ────────────────────────────────────────────────────────
variable "redis_version" {
  type        = string
  default     = "7.2"
  description = "Версия Redis (YC поддерживает 6.2, 7.0, 7.2)."
}

variable "redis_resource_preset" {
  type        = string
  default     = "hm2.micro"
  description = "Пресет ресурсов Redis (hm2.micro = 2 vCPU, 4 GiB)."
}

variable "redis_disk_size" {
  type        = number
  default     = 16
  description = "Размер диска Redis в GiB."
}

variable "redis_password" {
  type        = string
  description = "Пароль для Redis. Sensitive."
  sensitive   = true
}
