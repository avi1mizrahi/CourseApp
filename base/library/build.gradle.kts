
buildscript {
    repositories { jcenter() }

    val kotlinVersion = "1.3.31"

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    }
}

val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val kotlinVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val mockkVersion: String? by extra

apply(plugin = "kotlinx-serialization")

repositories {
    jcenter()
    // artifacts are published to this repository
    maven(url = "https://kotlin.bintray.com/kotlinx")
}

dependencies {
    compile("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", "1.2")

    compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compile("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.11.0")

    testCompile("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testCompile("com.natpryce:hamkrest:$hamkrestVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")

    runtime("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}