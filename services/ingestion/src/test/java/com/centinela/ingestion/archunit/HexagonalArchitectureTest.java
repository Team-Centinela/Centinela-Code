package com.centinela.ingestion.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.centinela.ingestion");

    @Test
    void domainLayerShouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.azure..")
                .allowEmptyShould(true)
                .because("Domain layer must be pure Java, free of framework annotations");

        rule.check(classes);
    }

    @Test
    void domainClassesShouldNotBeAnnotatedWithSpring() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().notBeAnnotatedWith(org.springframework.stereotype.Service.class)
                .andShould().notBeAnnotatedWith(org.springframework.stereotype.Component.class)
                .andShould().notBeAnnotatedWith(org.springframework.stereotype.Repository.class)
                .andShould().notBeAnnotatedWith(org.springframework.boot.autoconfigure.SpringBootApplication.class)
                .allowEmptyShould(true)
                .because("Domain classes must not carry Spring stereotype annotations");

        rule.check(classes);
    }

    @Test
    void infrastructureLayerShouldNotBeAccessedByDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .because("Domain must not depend on infrastructure adapters");

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
                .because("Hexagonal architecture: domain is innermost, application orchestrates, infrastructure adapts");

        rule.check(classes);
    }
}
