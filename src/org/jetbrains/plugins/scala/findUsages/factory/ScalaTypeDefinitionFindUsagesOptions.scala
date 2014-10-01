package org.jetbrains.plugins.scala
package findUsages.factory

import java.util.LinkedHashSet

import com.intellij.find.FindBundle
import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project

/**
 * @author Alefas
 * @since 15.12.12
 */
class ScalaTypeDefinitionFindUsagesOptions(project: Project) extends JavaFindUsagesOptions(project) {
  var isImplementingTypeDefinitions = false
  var isMembersUsages = false
  var isSearchCompanionModule = false

  override def equals(o: Any): Boolean = {
    o match {
      case other: ScalaTypeDefinitionFindUsagesOptions =>
        if (!super.equals(o)) return false
        if (other.isImplementingTypeDefinitions != isImplementingTypeDefinitions) return false
        if (other.isMembersUsages != isMembersUsages) return false
        if (other.isSearchCompanionModule != isSearchCompanionModule) return false
        true
      case _ => false
    }
  }

  override def hashCode(): Int = {
    var res = super.hashCode()
    res = 31 * res + (if (isImplementingTypeDefinitions) 1 else 0)
    res = 31 * res + (if (isMembersUsages) 1 else 0)
    res = 31 * res + (if (isSearchCompanionModule) 1 else 0)
    res
  }

  protected override def addUsageTypes(strings: LinkedHashSet[String]) {
    if (isUsages || isMembersUsages) {
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
