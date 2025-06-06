plugins {
    application
    id("com.gradleup.shadow") version "8.3.1"
}

application.mainClass = "dev.iseal.SSB.SSBMain"


group = "dev.iseal"
version = "1.2.0.0"

val jdaVersion = "5.5.1"
val logbackVersion = "1.5.18"



repositories {
    mavenCentral()
    // Add the JitPack repository for SimplixStorage
    maven("https://jitpack.io")
}

dependencies {
    implementation("net.dv8tion:JDA:$jdaVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.reflections:reflections:0.10.2")
    implementation("com.github.simplix-softworks:simplixstorage:3.2.7")
    implementation("com.github.docker-java:docker-java:3.5.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.5.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true

    // min Java version
    sourceCompatibility = "21"
}