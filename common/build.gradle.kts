import org.gradle.kotlin.dsl.named

dependencies {
    api(projects.viaversionApi)
    api(rootProject.libs.text) {
        exclude("com.google.code.gson", "gson")
    }
    testImplementation(rootProject.libs.netty)
    testImplementation(rootProject.libs.guava)
    testImplementation(rootProject.libs.snakeYaml)
    testImplementation(rootProject.libs.bundles.junit)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

java {
    withJavadocJar()
}

tasks.named<Jar>("sourcesJar") {
    from(project(":viaversion-api").sourceSets.main.get().allSource)
}

tasks.register<JavaExec>("runViaProxy") {
    dependsOn(tasks.shadowJar)
    val viaProxyConfiguration = configurations.create("viaProxy")
    viaProxyConfiguration.dependencies.add(dependencies.create(rootProject.libs.viaProxy.get().copy().setTransitive(false)))
    mainClass.set("net.raphimc.viaproxy.ViaProxy")
    classpath = viaProxyConfiguration
    workingDir = file("run")
    jvmArgs = listOf("-DskipUpdateCheck")
    if (System.getProperty("viaproxy.gui.autoStart") != null) {
        jvmArgs("-Dviaproxy.gui.autoStart")
    }
    doFirst {
        val jarsDir = file("$workingDir/jars")
        jarsDir.mkdirs()
        file("$jarsDir/${project.name}.jar").writeBytes(tasks.shadowJar.get().archiveFile.get().asFile.readBytes())
    }
    doLast {
        file("$workingDir/jars/${project.name}.jar").delete()
        file("$workingDir/logs").deleteRecursively()
    }
}

testing {
    suites.named<JvmTestSuite>("test") {
        useJUnitJupiter()
    }
}

