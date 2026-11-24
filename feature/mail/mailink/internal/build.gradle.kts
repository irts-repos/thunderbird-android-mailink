plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "net.thunderbird.feature.mail.mailink.internal"
}

dependencies {
    implementation(projects.feature.mail.mailink.api)
    implementation(projects.core.common)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.tb.mobile.components.ui.bolt)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
