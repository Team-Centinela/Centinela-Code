resource "azurerm_key_vault" "main" {
  name                       = local.key_vault_name
  resource_group_name        = var.resource_group_name
  location                   = var.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 7
  rbac_authorization_enabled = true
  purge_protection_enabled   = false

  network_acls {
    bypass         = "AzureServices"
    default_action = "Allow"
  }

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_role_assignment" "key_vault_secrets_officer_current" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_key_vault_secret" "servicebus_connection_string" {
  name         = "servicebus-connection-string"
  value        = azurerm_servicebus_namespace.main.default_primary_connection_string
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.key_vault_secrets_officer_current]
}

resource "azurerm_key_vault_secret" "appinsights_connection_string" {
  name         = "appinsights-connection-string"
  value        = azurerm_application_insights.main.connection_string
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.key_vault_secrets_officer_current]
}

resource "azurerm_key_vault_secret" "postgresql_aad_admin_password" {
  count        = var.postgresql_admin_password == null ? 0 : 1
  name         = "postgresql-aad-admin-password"
  value        = var.postgresql_admin_password
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.key_vault_secrets_officer_current]
}
