plugins {
    application
    id("com.gradleup.shadow") version "8.3.1"
}

application.mainClass = "dev.iseal.SSB.SSBMain"


group = "org.example"
version = "1.0"

val jdaVersion = "5.3.1"
val logbackVersion = "1.5.18"



repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.reflections:reflections:0.10.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    // min Java version
    sourceCompatibility = "21"
}