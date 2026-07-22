package com.centinela.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
        .importPackages("com.centinela");

    @Test
    void domainPackagesShouldNotDependOnSpring() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
            .check(classes);
    }

    @Test
    void moduleDomainPackagesShouldNotDependOnEachOther() {
        slices()
            .matching("com.centinela.(cases|alerts|reporting|auth|rules_config)..")
            .namingSlices("$1")
            .should().notDependOnEachOther()
            .check(classes);
    }

    @Test
    void noAccountOrTransactionPackage() {
        noClasses()
            .should().resideInAPackage("..account..")
            .orShould().resideInAPackage("..transaction..")
            .check(classes);
    }
}
