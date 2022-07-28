package org.jetbrains.plugins.scala
package findUsages.factory

import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project

class ScalaTypeDefinitionFindUsagesOptions(project: Project) extends JavaFindUsagesOptions(project) {
  isSearchForTextOccurrences = false

  var isImplementingTypeDefinitions = false
  var isMembersUsages = false
  var isSearchCompanionModule = false

  var isOnlyNewInstances: Boolean = false

  override def equals(o: Any): Boolean = {
    o match {
      case other: ScalaTypeDefinitionFindUsagesOptions =>
        super.equals(o) &&
          other.isImplementingTypeDefinitions == isImplementingTypeDefinitions &&
          other.isMembersUsages == isMembersUsages &&
          other.isSearchCompanionModule == isSearchCompanionModule &&
          other.isOnlyNewInstances == isOnlyNewInstances
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var res = super.hashCode()
    res = 31 * res + (if (isImplementingTypeDefinitions) 1 else 0)
    res = 31 * res + (if (isMembersUsages) 1 else 0)
    res = 31 * res + (if (isSearchCompanionModule) 1 else 0)
    res = 31 * res + (if (isOnlyNewInstances) 1 else 0)
    res
  }
}
