output "resource_group_name" {
  description = "Name of the workload resource group."
  value       = var.resource_group_name
}

output "log_analytics_workspace_id" {
  description = "Resource id of the Log Analytics workspace."
  value       = azurerm_log_analytics_workspace.main.id
}

output "log_analytics_workspace_name" {
  description = "Name of the Log Analytics workspace."
  value       = azurerm_log_analytics_workspace.main.name
}

output "application_insights_id" {
  description = "Resource id of Application Insights."
  value       = azurerm_application_insights.main.id
}

output "application_insights_name" {
  description = "Name of Application Insights."
  value       = azurerm_application_insights.main.name
}

output "application_insights_connection_string" {
  description = "Connection string for Application Insights (sensitive)."
  value       = azurerm_application_insights.main.connection_string
  sensitive   = true
}

output "key_vault_id" {
  description = "Resource id of the Key Vault."
  value       = azurerm_key_vault.main.id
}

output "key_vault_name" {
  description = "Name of the Key Vault."
  value       = azurerm_key_vault.main.name
}

output "key_vault_uri" {
  description = "URI of the Key Vault."
  value       = azurerm_key_vault.main.vault_uri
}

output "container_registry_id" {
  description = "Resource id of the Azure Container Registry."
  value       = azurerm_container_registry.main.id
}

output "container_registry_login_server" {
  description = "Login server of the Azure Container Registry."
  value       = azurerm_container_registry.main.login_server
}

output "container_registry_name" {
  description = "Name of the Azure Container Registry."
  value       = azurerm_container_registry.main.name
}

output "container_app_environment_id" {
  description = "Resource id of the Container Apps Environment."
  value       = azurerm_container_app_environment.main.id
}

output "container_app_environment_name" {
  description = "Name of the Container Apps Environment."
  value       = azurerm_container_app_environment.main.name
}

output "container_app_ids" {
  description = "Map of container app resource ids keyed by service name (ingestion, serverless-engine, core-backend, optionally ocr-worker)."
  value = merge(
    {
      ingestion         = azurerm_container_app.ingestion.id
      serverless-engine = azurerm_container_app.serverless_engine.id
      core-backend      = azurerm_container_app.core_backend.id
    },
    var.ocr_worker_enabled ? { ocr-worker = azurerm_container_app.ocr_worker[0].id } : {}
  )
}

output "container_app_fqdns" {
  description = "Map of container app FQDNs keyed by service name."
  value = merge(
    {
      ingestion         = azurerm_container_app.ingestion.ingress[0].fqdn
      serverless-engine = azurerm_container_app.serverless_engine.ingress[0].fqdn
      core-backend      = azurerm_container_app.core_backend.ingress[0].fqdn
    },
    var.ocr_worker_enabled ? { ocr-worker = azurerm_container_app.ocr_worker[0].ingress[0].fqdn } : {}
  )
}

output "container_app_principal_ids" {
  description = "Map of container app managed-identity principal ids keyed by service name."
  value = merge(
    {
      ingestion         = azurerm_container_app.ingestion.identity[0].principal_id
      serverless-engine = azurerm_container_app.serverless_engine.identity[0].principal_id
      core-backend      = azurerm_container_app.core_backend.identity[0].principal_id
    },
    var.ocr_worker_enabled ? { ocr-worker = azurerm_container_app.ocr_worker[0].identity[0].principal_id } : {}
  )
  sensitive = true
}

output "postgres_flexible_server_id" {
  description = "Resource id of the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server.main.id
}

output "postgres_flexible_server_name" {
  description = "Name of the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server.main.name
}

output "postgres_flexible_server_fqdn" {
  description = "FQDN of the PostgreSQL Flexible Server."
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "postgres_database_name" {
  description = "Name of the PostgreSQL database."
  value       = azurerm_postgresql_flexible_server_database.centinela.name
}

output "servicebus_namespace_id" {
  description = "Resource id of the Service Bus namespace."
  value       = azurerm_servicebus_namespace.main.id
}

output "servicebus_namespace_name" {
  description = "Name of the Service Bus namespace."
  value       = azurerm_servicebus_namespace.main.name
}

output "servicebus_topic_ids" {
  description = "Map of Service Bus topic resource ids keyed by topic name."
  value       = { for k, t in azurerm_servicebus_topic.topics : k => t.id }
}

output "servicebus_subscription_ids" {
  description = "Map of Service Bus subscription resource ids keyed by '<topic>-<sub>'."
  value       = { for k, s in azurerm_servicebus_subscription.subscriptions : k => s.id }
}

output "servicebus_queue_ids" {
  description = "Map of Service Bus queue resource ids keyed by queue name (including poison siblings)."
  value = merge(
    { for k, q in azurerm_servicebus_queue.queues : k => q.id },
    { for k, q in azurerm_servicebus_queue.poison : k => q.id },
  )
}

output "servicebus_primary_connection_string" {
  description = "Primary connection string of the Service Bus namespace (sensitive)."
  value       = azurerm_servicebus_namespace.main.default_primary_connection_string
  sensitive   = true
}

output "storage_account_id" {
  description = "Resource id of the Storage Account."
  value       = azurerm_storage_account.main.id
}

output "storage_account_name" {
  description = "Name of the Storage Account."
  value       = azurerm_storage_account.main.name
}

output "storage_account_primary_blob_endpoint" {
  description = "Primary blob endpoint of the Storage Account."
  value       = azurerm_storage_account.main.primary_blob_endpoint
}

output "documents_worm_container_name" {
  description = "Name of the WORM container for verification documents (ADR-002)."
  value       = azurerm_storage_container.documents_worm.name
}

output "budget_id" {
  description = "Resource id of the consumption budget."
  value       = azurerm_consumption_budget_resource_group.main.id
}

output "budget_action_group_id" {
  description = "Resource id of the budget action group."
  value       = azurerm_monitor_action_group.budget.id
}
