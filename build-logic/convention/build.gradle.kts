plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.gy.githubclient.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(gradleApi())
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
}

gradlePlugin {
    plugins {
        create("androidApp") {
            id = "gy.android.app"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        create("androidLib") {
            id = "gy.android.lib"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        create("kotlinLib") {
            id = "gy.kotlin.lib"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        create("compose") {
            id = "gy.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        create("quality") {
            id = "gy.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
