package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project

/**
  * @author Ignat Loskutov
  */
class ScalaImplicitDefinitionFindUsagesOptions(project: Project) extends JavaFindUsagesOptions(project) {
  isSearchForTextOccurrences = false
}
