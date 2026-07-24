package com.centinela.serverless.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.centinela.serverless");

    @Test
    void domainShouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.azure..")
                .allowEmptyShould(true)
                .because("Domain layer must remain pure Java per ADR-001");
        rule.check(classes);
    }

    @Test
    void domainShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .because("Domain must depend only on ports, not on adapters (ADR-001)");
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
                .allowEmptyShould(true)
                .because("Hexagonal layering (ADR-001)");
        rule.check(classes);
    }
}
