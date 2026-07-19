package com.example.tests.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests — enforces the Hexagonal Architecture rules on every build.
 *
 * <h2>Rules enforced:</h2>
 * <ol>
 *   <li>Core package has ZERO Spring imports</li>
 *   <li>Inbound adapters NEVER directly call outbound adapters</li>
 *   <li>Outbound adapters implement the {@code StoragePort} interface</li>
 *   <li>Spring {@code @Configuration} lives ONLY in the application package</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.example")
public class HexagonalArchitectureTest {

    /**
     * Rule 1: Core domain must have ZERO Spring dependencies.
     * If this breaks, someone put @Service or @Component into ecs-core — unacceptable.
     */
    @ArchTest
    public static final ArchRule core_must_have_no_spring_dependencies =
            noClasses()
                    .that().resideInAPackage("com.example.core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.inject.."
                    )
                    .because("Core domain must be framework-agnostic (Hexagonal Architecture Rule 1)");

    /**
     * Rule 2: Inbound adapters must NOT directly import outbound adapter classes.
     * Communication must go through domain ports.
     */
    @ArchTest
    public static final ArchRule inbound_adapters_must_not_depend_on_outbound_adapters =
            noClasses()
                    .that().resideInAPackage("com.example.adapters.inbound..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.adapters.outbound..")
                    .because("Inbound adapters must call inbound ports, not outbound adapters (Rule 2)");

    /**
     * Rule 3: The S3StorageAdapter must implement the StoragePort interface.
     * Outbound adapters must always implement a port — never be called directly.
     */
    @ArchTest
    public static final ArchRule outbound_adapters_must_implement_ports =
            classes()
                    .that().resideInAPackage("com.example.adapters.outbound..")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(com.example.core.ports.StoragePort.class)
                    .because("Outbound adapters must implement domain ports (Rule 3)");

    /**
     * Rule 4: Only the application module may contain @Configuration classes.
     * Adapters must NEVER define beans.
     */
    @ArchTest
    public static final ArchRule configuration_only_in_application_module =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.example.adapters..",
                            "com.example.core.."
                    )
                    .should().beAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                    .because("Spring @Configuration must live ONLY in the application module (Rule 4)");
}

