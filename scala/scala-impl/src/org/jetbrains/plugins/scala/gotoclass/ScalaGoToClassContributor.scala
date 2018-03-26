package org.jetbrains.plugins.scala
package gotoclass

import com.intellij.navigation.{ChooseByNameContributor, NavigationItem}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 12/19/13
 */
class ScalaGoToClassContributor extends ChooseByNameContributor {
  def getNames(project: Project, includeNonProjectItems: Boolean): Array[String] = {
    val classNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, project)
    val packageObjectNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY, project)
    (classNames.asScala ++ packageObjectNames.asScala).toArray
  }

  def getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array[NavigationItem] = {
    val scope = if (includeNonProjectItems) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
    val cleanName = ScalaNamesUtil.cleanFqn(name)

    import ScalaIndexKeys._
    val classes = NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY.elements(cleanName, scope, classOf[PsiClass])(project)
    val packageObjects = PACKAGE_OBJECT_SHORT_NAME_KEY.elements(cleanName, scope, classOf[PsiClass])(project)
    (classes ++ packageObjects).toArray
  }
}
