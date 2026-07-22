package com.centinela.corebackend.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.centinela.corebackend");

    @Test
    void domainLayerShouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .because("Domain layer must be pure Java, free of framework annotations")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void infrastructureLayerShouldNotBeAccessedByDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("Domain must not depend on infrastructure adapters")
                .allowEmptyShould(true);

        rule.check(classes);
    }

    @Test
    void noAccountOrTransactionPackageShouldExist() {
        ArchRule rule = noClasses()
                .should().resideInAPackage("..account..")
                .orShould().resideInAPackage("..transaction..")
                .because("account/ and transaction/ do not belong in Core Backend per ADR-002 (decision #61)");
        rule.check(classes);
    }

    @Test
    void layeredArchitectureShouldBeRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("domain").definedBy("..domain..")
                .layer("application").definedBy("..application..")
                .layer("infrastructure").definedBy("..infrastructure..")
                .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "infrastructure")
                .whereLayer("application").mayOnlyBeAccessedByLayers("infrastructure")
                .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer()
                .because("Hexagonal architecture: domain is innermost, application orchestrates, infrastructure adapts");

        rule.allowEmptyShould(true).check(classes);
    }
}
