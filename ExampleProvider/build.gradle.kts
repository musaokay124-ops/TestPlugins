dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Versiyon numarası
version = 1

cloudstream {
    // DERLEYİCİNİN EKLENTİYİ PAKETLEMESİ İÇİN ŞART OLAN KISIM:
    setPlugins(
        "HDFilmCehennemi"
    )

    description = "HDFilmCehennemi Cloudstream Eklentisi"
    authors = listOf("musaokay124-ops")

    status = 1

    tvTypes = listOf("Movie", "TvSeries")

    requiresResources = true
    language = "tr"

    iconUrl = "https://www.hdfilmcehennemi.nl/favicon.ico"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
