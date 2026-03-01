package com.example.rustorecatalog.install

sealed interface InstallState {
    object NotInstalled : InstallState
    data class Downloading(val progress: Int) : InstallState
    object Downloaded : InstallState
    object Installing : InstallState
    object Uninstalling : InstallState
    object Installed : InstallState
    data class Error(val message: String) : InstallState
}

