package org.jetbrains.plugins.cbt.project.settings

import java.io.File

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

class CbtExecutionSettings(val realProjectPath: String,
                           val isCbt: Boolean,
                           val extraModules: Seq[File]) extends ExternalSystemExecutionSettings