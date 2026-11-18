package io.interviewcodi.backend.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
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

    // 계층: 도메인 패키지 아래 controller·worker → service → repository. domain·dto는 어느 계층에서나 쓴다.

    @ArchTest
    static final ArchRule CONTROLLERS_RESIDE_IN_CONTROLLER_PACKAGE = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller");

    @ArchTest
    static final ArchRule SERVICES_RESIDE_IN_SERVICE_PACKAGE = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..service");

    @ArchTest
    static final ArchRule REPOSITORIES_RESIDE_IN_REPOSITORY_PACKAGE = classes()
            .that().areAnnotatedWith(Repository.class)
            .should().resideInAPackage("..repository");

    @ArchTest
    static final ArchRule HANDLERS_RETURN_RESPONSE_ENTITY = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should().haveRawReturnType(ResponseEntity.class)
            .because("상태 코드와 헤더를 응답마다 드러나게 정한다");

    @ArchTest
    static final ArchRule LAYERS_ARE_CALLED_TOP_DOWN = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller")
            .layer("Service").definedBy("..service")
            .layer("Repository").definedBy("..repository")
            .layer("Worker").definedBy("..worker")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Worker").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service", "Worker")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule REPOSITORIES_ARE_USED_ONLY_IN_THEIR_DOMAIN = classes()
            .that().resideInAPackage("..repository")
            .should(beAccessedOnlyFromTheirOwnDomain())
            .because("다른 도메인의 데이터는 그 도메인의 서비스를 거쳐 다룬다");

    @ArchTest
    static final ArchRule DOMAIN_AND_DTO_DO_NOT_DEPEND_ON_LAYERS = noClasses()
            .that().resideInAnyPackage("..domain", "..dto")
            .should().dependOnClassesThat().resideInAnyPackage("..controller", "..service", "..repository");

    @ArchTest
    static final ArchRule ONLY_REPOSITORIES_USE_JDBC_CLIENT = noClasses()
            .that().areNotAnnotatedWith(Repository.class)
            .should().dependOnClassesThat().areAssignableTo(JdbcClient.class);

    @ArchTest
    static final ArchRule SERVICES_DO_NOT_USE_SERVLET_API = noClasses()
            .that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..");

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

    /** {@code io.interviewcodi.backend.<도메인>...}에서 도메인 이름을 꺼낸다. */
    private static String domainOf(JavaClass javaClass) {
        String rest = javaClass.getPackageName().substring(ROOT.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static ArchCondition<JavaClass> beAccessedOnlyFromTheirOwnDomain() {
        return new ArchCondition<>("be accessed only from their own domain") {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                String domain = domainOf(repository);
                repository.getDirectDependenciesToSelf().stream()
                        .filter(dependency -> dependency.getOriginClass().getPackageName().startsWith(ROOT + "."))
                        .filter(dependency -> !domainOf(dependency.getOriginClass()).equals(domain))
                        .forEach(dependency -> events.add(SimpleConditionEvent.violated(dependency,
                                dependency.getDescription())));
            }
        };
    }
}
