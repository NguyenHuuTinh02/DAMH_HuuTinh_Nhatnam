// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google() // Đảm bảo rằng kho Google đã được thêm vào
        mavenCentral() // Thêm kho Maven Central nếu chưa có
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
    }
}