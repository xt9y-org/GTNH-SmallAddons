plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

dependencies {
    // Matches the 1.7.10 GTNH LittleTiles API, not the modern CreativeMD implementation.
    api("com.github.GTNewHorizons:LittleTiles:1.6.53:dev")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test.configure {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}
