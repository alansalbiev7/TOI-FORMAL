plugins {
    scala
    application
}
repositories { mavenCentral() }
group = "ru.toi"
version = "0.2.0-SNAPSHOT"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
dependencies {
    implementation("org.scala-lang:scala3-library_3:3.3.3")
    implementation("org.apache.jena:jena-shacl:4.10.0")
    implementation("org.apache.jena:jena-arq:4.10.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.9")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
application { mainClass.set("ru.smev.asg.Main") }
tasks.test { useJUnitPlatform(); failFast = false }
tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters = listOf("-feature")
}
dependencyLocking { lockAllConfigurations() }
