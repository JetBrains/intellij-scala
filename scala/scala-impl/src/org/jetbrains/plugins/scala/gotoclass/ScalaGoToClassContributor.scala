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

import org.jetbrains.plugins.scala.finder.ScalaFilterScope

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
    val scalaScope = ScalaFilterScope(project, scope)
    val cleanName = ScalaNamesUtil.cleanFqn(name)
    val classes = StubIndex.getElements(ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, cleanName, project, scalaScope, classOf[PsiClass])
    val packageObjects = StubIndex.getElements(ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY, cleanName, project, scalaScope, classOf[PsiClass])
    (classes.asScala ++ packageObjects.asScala).toArray
  }
}
