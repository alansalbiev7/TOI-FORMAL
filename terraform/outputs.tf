# terraform/outputs.tf — выходные значения инфраструктуры ASG
# Используются для конфигурации kubeconfig и CI/CD пайплайнов.
# ─────────────────────────────────────────────────────────────────────────────

output "cluster_id" {
  value       = yandex_kubernetes_cluster.asg.id
  description = "Идентификатор Managed K8s кластера ASG."
}

output "cluster_name" {
  value       = yandex_kubernetes_cluster.asg.name
  description = "Имя кластера ASG."
}

output "cluster_endpoint" {
  value       = yandex_kubernetes_cluster.asg.master[0].internal_v4_endpoint
  description = "Внутренний endpoint API сервера K8s (используется для подключения из VPC)."
}

output "cluster_external_endpoint" {
  value       = yandex_kubernetes_cluster.asg.master[0].external_v4_endpoint
  description = "Внешний endpoint API сервера K8s (для CI/CD)."
  sensitive   = false
}

output "cluster_ca_certificate" {
  value       = yandex_kubernetes_cluster.asg.master[0].cluster_ca_certificate
  description = "CA-сертификат кластера (для kubeconfig)."
  sensitive   = true
}

output "registry_id" {
  value       = yandex_container_registry.asg.id
  description = "Идентификатор Container Registry."
}

output "registry_endpoint" {
  value       = "cr.yandex/${yandex_container_registry.asg.id}"
  description = "Полный путь registry для docker push (cr.yandex/<registry_id>)."
}

output "registry_name" {
  value       = yandex_container_registry.asg.name
  description = "Имя Container Registry."
}

output "node_group_id" {
  value       = yandex_kubernetes_node_group.asg_workers.id
  description = "Идентификатор node group ASG."
}

output "postgres_host" {
  value       = "c-${yandex_mdb_postgresql_cluster.asg.id}.mdb.yandexcloud.net"
  description = "FQDN PostgreSQL-кластера для подключения asg-core."
}

output "postgres_user" {
  value       = yandex_mdb_postgresql_user.asg.name
  description = "Имя пользователя PostgreSQL (asg)."
}

output "postgres_database" {
  value       = yandex_mdb_postgresql_database.asg.name
  description = "Имя базы данных PostgreSQL (asg)."
}

output "redis_host" {
  value       = "c-${yandex_mdb_redis_cluster.asg.id}.mdb.yandexcloud.net"
  description = "FQDN Redis-кластера (master-узел)."
}

output "redis_port" {
  value       = 6379
  description = "Порт Redis."
}

output "ingress_public_ip" {
  value       = yandex_vpc_address.asg_ingress_ip.external_ipv4_address[0].address
  description = "Публичный IP для Ingress-контроллера ASG."
}

# ─── Сводка для быстрой проверки ──────────────────────────────────────────
output "summary" {
  value = {
    cluster_name     = yandex_kubernetes_cluster.asg.name
    cluster_version  = yandex_kubernetes_cluster.asg.master[0].version
    node_count       = 3
    node_preset      = "e2-medium (2 vCPU, 4 GiB)"
    postgres_version = yandex_mdb_postgresql_cluster.asg.config[0].version
    redis_version    = yandex_mdb_redis_cluster.asg.config[0].version
    registry         = "cr.yandex/${yandex_container_registry.asg.id}"
  }
  description = "Сводка инфраструктуры ASG."
}
