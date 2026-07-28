provider "azurerm" {
  subscription_id = var.subscription_id
  tenant_id       = var.tenant_id

  features {
    resource_group {
      prevent_deletion_if_contains_resources = true
    }
  }

  storage_use_azuread = true
}

provider "azuread" {
  tenant_id = var.tenant_id
}
