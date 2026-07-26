terraform {
  backend "azurerm" {
    resource_group_name  = "rg-tfstate-bootstrap"
    storage_account_name = "stcentinelatfstate"
    container_name       = "tfstate"
    key                  = "centinela.tfstate"
    use_azuread_auth     = true
  }
}
