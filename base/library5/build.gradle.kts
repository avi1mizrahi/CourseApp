val junitVersion = "5.5.0-M1"
val hamkrestVersion = "1.7.0.0"
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val mockkVersion: String? by extra

dependencies {
    compile("com.google.inject", "guice", guiceVersion)
    compile("com.authzee.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)
    compile("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", "1.2")
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testCompile("com.natpryce:hamkrest:$hamkrestVersion")

    runtime("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}