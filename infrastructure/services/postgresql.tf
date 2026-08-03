resource "azurerm_postgresql_flexible_server" "main" {
  name                  = local.postgres_name
  resource_group_name   = var.resource_group_name
  location              = var.location
  version               = "16"
  sku_name              = "B_Standard_B1ms"
  storage_mb            = 32768
  backup_retention_days = 7

  authentication {
    active_directory_auth_enabled = true
    password_auth_enabled         = false
    tenant_id                     = data.azurerm_client_config.current.tenant_id
  }

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_postgresql_flexible_server_active_directory_administrator" "main" {
  server_name         = azurerm_postgresql_flexible_server.main.name
  resource_group_name = var.resource_group_name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  object_id           = var.postgresql_aad_administrator_object_id != "" ? var.postgresql_aad_administrator_object_id : data.azurerm_client_config.current.object_id
  principal_name      = var.postgresql_aad_administrator_login
  principal_type      = "ServicePrincipal"
}

resource "azurerm_postgresql_flexible_server_database" "centinela" {
  name      = "centinela"
  server_id = azurerm_postgresql_flexible_server.main.id
  collation = "en_US.utf8"
  charset   = "utf8"
}

resource "azurerm_postgresql_flexible_server_configuration" "extensions" {
  name      = "azure.extensions"
  server_id = azurerm_postgresql_flexible_server.main.id
  value     = join(",", [for extension in var.postgresql_extensions : upper(extension)])
}
