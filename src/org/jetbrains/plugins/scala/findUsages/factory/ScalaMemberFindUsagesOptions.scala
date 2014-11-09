package org.jetbrains.plugins.scala
package findUsages.factory

import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project

/**
 * Nikolay.Tropin
 * 2014-09-15
 */
class ScalaMemberFindUsagesOptions(project: Project) extends JavaFindUsagesOptions(project) {
  isSearchForTextOccurrences = false
}
