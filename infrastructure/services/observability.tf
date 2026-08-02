data "azurerm_client_config" "current" {}

resource "azurerm_log_analytics_workspace" "main" {
  name                = local.log_analytics_name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_application_insights" "main" {
  name                       = local.app_insights_name
  resource_group_name        = var.resource_group_name
  location                   = var.location
  workspace_id               = azurerm_log_analytics_workspace.main.id
  application_type           = "web"
  sampling_percentage        = 100
  daily_data_cap_in_gb       = 1
  retention_in_days          = 30
  internet_ingestion_enabled = true
  internet_query_enabled     = true

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_container_app_environment" "main" {
  name                       = local.aca_environment_name
  resource_group_name        = var.resource_group_name
  location                   = var.location
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id

  tags = merge(var.common_tags, var.mode_b_tags)
}
