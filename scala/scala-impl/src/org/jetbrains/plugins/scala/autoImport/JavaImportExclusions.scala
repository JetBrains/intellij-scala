package org.jetbrains.plugins.scala.autoImport

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.openapi.project.Project

final class JavaImportExclusions extends ImportExclusions {
  override def isExcluded(qualifiedName: String, project: Project): Boolean =
    JavaProjectCodeInsightSettings.getSettings(project).isExcluded(qualifiedName)
}
