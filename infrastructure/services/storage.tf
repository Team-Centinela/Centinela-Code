resource "azurerm_storage_account" "main" {
  name                            = local.storage_account_name
  resource_group_name             = var.resource_group_name
  location                        = var.location
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  account_kind                    = "StorageV2"
  min_tls_version                 = "TLS1_2"
  https_traffic_only_enabled      = true
  allow_nested_items_to_be_public = false
  shared_access_key_enabled       = false
  default_to_oauth_authentication = true

  network_rules {
    bypass         = ["AzureServices"]
    default_action = "Allow"
  }

  blob_properties {
    versioning_enabled  = true
    change_feed_enabled = true

    container_delete_retention_policy {
      days = 7
    }

    delete_retention_policy {
      days = 7
    }
  }

  tags = merge(var.common_tags, var.mode_b_tags)
}

resource "azurerm_storage_container" "documents_worm" {
  name                  = local.storage_documents_container
  storage_account_id    = azurerm_storage_account.main.id
  container_access_type = "private"
}

resource "azurerm_storage_management_policy" "documents_worm_lifecycle" {
  storage_account_id = azurerm_storage_account.main.id

  rule {
    name    = "documents-worm-lifecycle"
    enabled = true

    filters {
      prefix_match = ["${local.storage_documents_container}/"]
      blob_types   = ["blockBlob"]
    }

    actions {
      base_blob {
        tier_to_cool_after_days_since_modification_greater_than    = 90
        tier_to_archive_after_days_since_modification_greater_than = 365
      }
    }
  }
}

resource "azurerm_storage_container_immutability_policy" "documents_worm" {
  storage_container_resource_manager_id = azurerm_storage_container.documents_worm.id
  immutability_period_in_days           = 2557
  protected_append_writes_all_enabled   = true
}

resource "azurerm_role_assignment" "worm_storage_owner" {
  count = var.worm_owner_group_principal != "" ? 1 : 0

  scope                = azurerm_storage_account.main.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = var.worm_owner_group_principal
}
