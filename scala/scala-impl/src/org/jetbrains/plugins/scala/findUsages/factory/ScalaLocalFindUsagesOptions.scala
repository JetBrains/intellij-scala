package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.openapi.project.Project

final class ScalaLocalFindUsagesOptions(project: Project) extends ScalaFindUsagesOptionsBase(project) {
  isSearchForTextOccurrences = false
}
