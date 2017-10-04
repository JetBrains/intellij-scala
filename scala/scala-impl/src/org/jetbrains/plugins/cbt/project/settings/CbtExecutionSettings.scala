package org.jetbrains.plugins.cbt.project.settings

import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings

class CbtExecutionSettings(val realProjectPath: String,
                           val isCbt: Boolean,
                           val useCbtForInternalTasks: Boolean,
                           val useDirect: Boolean,
                           val extraModules: Seq[String]) extends ExternalSystemExecutionSettings