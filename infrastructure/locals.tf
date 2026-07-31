locals {
  rg_name = "rg-${var.project}-${var.environment}"

  common_tags = {
    project       = var.project
    environment   = var.environment
    managed_by    = "terraform"
    cost_center   = "centinela-platform"
    # AAD owner email is parameterized (ADR-002 §WORM + ADR-007 §7.7). The
    # Phase 0 default resolves to the operator's existing tenant; promote
    # to centinela-platform@<tenant> once the AAD group lands.
    owner         = var.aad_owner_email
    documentation = "../docs/architecture/06-technology-stack.md"
  }
}
