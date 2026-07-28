locals {
  rg_name = "rg-${var.project}-${var.environment}"

  common_tags = {
    project       = var.project
    environment   = var.environment
    managed_by    = "terraform"
    cost_center   = "centinela-platform"
    owner         = "centinela-platform@centinela.onmicrosoft.com"
    documentation = "../docs/architecture/06-technology-stack.md"
  }
}
