plugins {
    java
    id("org.springframework.boot") version "4.0.6"
}

group = "kr.ac.koreatech.indoor"
version = "0.0.1-SNAPSHOT"
description = "Indoor VPS backend migrated to Spring Boot"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.6"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("net.postgis:postgis-jdbc:2025.1.1")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform("io.cucumber:cucumber-bom:7.34.3"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.2"))
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("io.cucumber:cucumber-junit-platform-engine")
}

tasks.test {
    useJUnitPlatform {
        excludeEngines("cucumber")
        excludeTags("e2e")
    }
}

tasks.register<Test>("e2eTest") {
    description = "Runs isolated end-to-end tests against Testcontainers dependencies."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty(
        "indoor.e2e.db.image",
        providers.gradleProperty("indoor.e2e.db.image")
            .orElse("garapadev/postgres-postgis-pgvector:15-stable")
            .get()
    )
    systemProperty("cucumber.features", "classpath:features")
    systemProperty("cucumber.glue", "kr.ac.koreatech.indoor.vps.acceptance")
    systemProperty("cucumber.filter.tags", "@acceptance")
    systemProperty("cucumber.plugin", "pretty,summary")
    shouldRunAfter(tasks.named("test"))
    useJUnitPlatform {
        includeEngines("cucumber")
    }
}
