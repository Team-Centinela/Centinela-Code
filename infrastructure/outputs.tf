output "resource_group_name" {
  description = "Name of the main resource group holding all Centinela resources."
  value       = azurerm_resource_group.main.name
}

output "resource_group_id" {
  description = "Azure resource id of the main resource group."
  value       = azurerm_resource_group.main.id
}

output "location" {
  description = "Azure region used by the main resource group."
  value       = azurerm_resource_group.main.location
}

output "subscription_suffix" {
  description = "Short deterministic suffix (6 chars) derived from subscription id, used to make globally-unique Azure resource names deterministic."
  value       = local.suffix
}

output "log_analytics_workspace_id" {
  description = "Resource id of the Log Analytics workspace."
  value       = module.services.log_analytics_workspace_id
}

output "application_insights_id" {
  description = "Resource id of Application Insights."
  value       = module.services.application_insights_id
}

output "application_insights_name" {
  description = "Name of Application Insights."
  value       = module.services.application_insights_name
}

output "application_insights_connection_string" {
  description = "Connection string for Application Insights (sensitive)."
  value       = module.services.application_insights_connection_string
  sensitive   = true
}

output "key_vault_id" {
  description = "Resource id of the Key Vault."
  value       = module.services.key_vault_id
}

output "key_vault_name" {
  description = "Name of the Key Vault."
  value       = module.services.key_vault_name
}

output "key_vault_uri" {
  description = "URI of the Key Vault."
  value       = module.services.key_vault_uri
}

output "container_registry_id" {
  description = "Resource id of the Azure Container Registry."
  value       = module.services.container_registry_id
}

output "container_registry_login_server" {
  description = "Login server of the Azure Container Registry."
  value       = module.services.container_registry_login_server
}

output "container_registry_name" {
  description = "Name of the Azure Container Registry."
  value       = module.services.container_registry_name
}

output "container_app_environment_id" {
  description = "Resource id of the Container Apps Environment."
  value       = module.services.container_app_environment_id
}

output "container_app_environment_name" {
  description = "Name of the Container Apps Environment."
  value       = module.services.container_app_environment_name
}

output "container_app_ids" {
  description = "Map of container app resource ids keyed by service name."
  value       = module.services.container_app_ids
}

output "container_app_fqdns" {
  description = "Map of container app FQDNs keyed by service name."
  value       = module.services.container_app_fqdns
}

output "container_app_principal_ids" {
  description = "Map of container app managed-identity principal ids keyed by service name."
  value       = module.services.container_app_principal_ids
  sensitive   = true
}

output "postgres_flexible_server_id" {
  description = "Resource id of the PostgreSQL Flexible Server."
  value       = module.services.postgres_flexible_server_id
}

output "postgres_flexible_server_name" {
  description = "Name of the PostgreSQL Flexible Server."
  value       = module.services.postgres_flexible_server_name
}

output "postgres_flexible_server_fqdn" {
  description = "FQDN of the PostgreSQL Flexible Server."
  value       = module.services.postgres_flexible_server_fqdn
}

output "postgres_database_name" {
  description = "Name of the PostgreSQL database."
  value       = module.services.postgres_database_name
}

output "servicebus_namespace_id" {
  description = "Resource id of the Service Bus namespace."
  value       = module.services.servicebus_namespace_id
}

output "servicebus_namespace_name" {
  description = "Name of the Service Bus namespace."
  value       = module.services.servicebus_namespace_name
}

output "servicebus_topic_ids" {
  description = "Map of Service Bus topic resource ids keyed by topic name."
  value       = module.services.servicebus_topic_ids
}

output "servicebus_subscription_ids" {
  description = "Map of Service Bus subscription resource ids keyed by '<topic>-<sub>'."
  value       = module.services.servicebus_subscription_ids
}

output "servicebus_queue_ids" {
  description = "Map of Service Bus queue resource ids keyed by queue name (including poison siblings)."
  value       = module.services.servicebus_queue_ids
}

output "servicebus_primary_connection_string" {
  description = "Primary connection string of the Service Bus namespace (sensitive)."
  value       = module.services.servicebus_primary_connection_string
  sensitive   = true
}

output "storage_account_id" {
  description = "Resource id of the Storage Account."
  value       = module.services.storage_account_id
}

output "storage_account_name" {
  description = "Name of the Storage Account."
  value       = module.services.storage_account_name
}

output "storage_account_primary_blob_endpoint" {
  description = "Primary blob endpoint of the Storage Account."
  value       = module.services.storage_account_primary_blob_endpoint
}

output "documents_worm_container_name" {
  description = "Name of the WORM container for verification documents (ADR-002)."
  value       = module.services.documents_worm_container_name
}

output "budget_id" {
  description = "Resource id of the consumption budget."
  value       = module.services.budget_id
}

output "budget_action_group_id" {
  description = "Resource id of the budget action group."
  value       = module.services.budget_action_group_id
}
