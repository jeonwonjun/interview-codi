package io.interviewcodi.backend.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/coding-conventions.md의 구조 규칙. 규칙을 바꾸면 문서와 함께 고친다.
 * 대상 클래스가 하나도 없으면 규칙이 실패한다(ArchUnit 기본값). 규칙이 아무것도 검사하지 않고 통과하는 것을 막는다.
 */
@AnalyzeClasses(packages = "io.interviewcodi.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "io.interviewcodi.backend";

    // 이름

    @ArchTest
    static final ArchRule CONTROLLERS_ARE_NAMED_CONTROLLER = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule SERVICES_ARE_NAMED_SERVICE = classes()
            .that().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule REPOSITORIES_ARE_NAMED_REPOSITORY = classes()
            .that().areAnnotatedWith(Repository.class)
            .should().haveSimpleNameEndingWith("Repository");

    // 패키지

    @ArchTest
    static final ArchRule FEATURE_PACKAGES_HAVE_NO_CYCLES = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule COMMON_DOES_NOT_DEPEND_ON_FEATURES = noClasses()
            .that().resideInAPackage(ROOT + ".common..")
            .should().dependOnClassesThat(resideInAPackage(ROOT + "..").and(not(resideInAPackage(ROOT + ".common.."))));

    @ArchTest
    static final ArchRule DTOS_ARE_RECORDS = classes()
            .that().resideInAPackage("..dto..")
            .should().beAssignableTo(Record.class);

    @ArchTest
    static final ArchRule DTOS_ARE_REQUESTS_OR_RESPONSES = classes()
            .that().resideInAPackage("..dto..")
            .should().haveSimpleNameEndingWith("Request")
            .orShould().haveSimpleNameEndingWith("Response");

    // 계층

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_USE_REPOSITORIES = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(Repository.class);

    @ArchTest
    static final ArchRule ONLY_REPOSITORIES_USE_JDBC_CLIENT = noClasses()
            .that().areNotAnnotatedWith(Repository.class)
            .should().dependOnClassesThat().areAssignableTo(JdbcClient.class);

    @ArchTest
    static final ArchRule SERVICES_DO_NOT_USE_SERVLET_API = noClasses()
            .that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..");

    @ArchTest
    static final ArchRule SERVICES_DO_NOT_DEPEND_ON_CONTROLLERS = noClasses()
            .that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().areAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule NO_SPRING_SECURITY = noClasses()
            .should().dependOnClassesThat().resideInAPackage("org.springframework.security..")
            .because("로그인은 Spring Security 없이 직접 구현한다(결정 기록 0006)");

    // 일반

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule NO_GENERIC_EXCEPTIONS = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    @ArchTest
    static final ArchRule NO_STANDARD_STREAMS = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule NO_JAVA_UTIL_LOGGING = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
