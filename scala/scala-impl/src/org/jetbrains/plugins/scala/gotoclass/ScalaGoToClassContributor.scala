package org.jetbrains.plugins.scala
package gotoclass

import com.intellij.navigation.{GotoClassContributor, NavigationItem}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 12/19/13
 */
class ScalaGoToClassContributor extends GotoClassContributor {

  import ScalaIndexKeys._

  override def getNames(project: Project, includeNonProjectItems: Boolean): Array[String] = {
    implicit val projectCopy: Project = project
    (NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY.allKeys ++ PACKAGE_OBJECT_SHORT_NAME_KEY.allKeys).toArray
  }

  override def getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array[NavigationItem] = {
    val scope = if (includeNonProjectItems) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
    val cleanName = ScalaNamesUtil.cleanFqn(name)

    implicit val projectCopy: Project = project

    val classes = NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY.elements(cleanName, scope)
    val packageObjects = PACKAGE_OBJECT_SHORT_NAME_KEY.elements(cleanName, scope)
    (classes ++ packageObjects).toArray
  }

  override def getQualifiedName(item: NavigationItem): String = item match {
    case t: ScTypeDefinition => t.qualifiedName
    case _ => null
  }

  override def getQualifiedNameSeparator: String = "."
}
