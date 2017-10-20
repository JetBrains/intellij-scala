package org.jetbrains.plugins.scala
package findUsages.factory

import java.util

import com.intellij.find.FindBundle
import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project

/**
  * @author Alefas
  * @since 15.12.12
  */
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

  protected override def addUsageTypes(strings: util.LinkedHashSet[String]) {
    if (isOnlyNewInstances) {
      strings.add(ScalaBundle.message("find.usages.instances.title"))
    } else if (isUsages || isMembersUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"))
    }

    if (isImplementingTypeDefinitions) {
      strings.add(ScalaBundle.message("find.usages.implementing.type.definition"))
    }

    if (isSearchCompanionModule) {
      strings.add(ScalaBundle.message("find.usages.companin.module"))
    }
  }
}
