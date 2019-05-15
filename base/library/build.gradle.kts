
val junitVersion = "5.5.0-M1"
val hamkrestVersion = "1.7.0.0"
val mockkVersion = "1.9.3.kotlin12"
val kotlinVersion = "1.3.30"

buildscript {
    repositories { jcenter() }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    }
}

apply(plugin = "kotlinx-serialization")

repositories {
    jcenter()
    // artifacts are published to this repository
    maven(url = "https://kotlin.bintray.com/kotlinx")
}

dependencies {
    compile("il.ac.technion.cs.softwaredesign:primitive-storage-layer:1.1")

    compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    compile("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.11.0")

    testCompile("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testCompile("com.natpryce:hamkrest:$hamkrestVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")

    runtime("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}