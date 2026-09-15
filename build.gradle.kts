plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test.configure {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    api("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.594:dev")
    api("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev")
    api("com.github.GTNewHorizons:ModularUI2:2.3.73-1.7.10:dev")
    api("com.github.GTNewHorizons:GTNHLib:0.11.9:dev")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
