plugins {
    id(ThunderbirdPlugins.Library.android)
}

android {
    namespace = "net.thunderbird.feature.mail.mailink.api"
}

dependencies {
    api(projects.core.common)
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
