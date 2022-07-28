package org.jetbrains.plugins.scala
package findUsages.factory

import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project

class ScalaMemberFindUsagesOptions(project: Project) extends JavaFindUsagesOptions(project) {
  isSearchForTextOccurrences = false
}

class ScalaLocalFindUsagesOptions(project: Project) extends JavaFindUsagesOptions(project) {
  isSearchForTextOccurrences = false
}
