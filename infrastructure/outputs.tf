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
