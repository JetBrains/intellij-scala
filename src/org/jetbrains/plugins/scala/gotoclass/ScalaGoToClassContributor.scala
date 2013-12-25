package org.jetbrains.plugins.scala
package gotoclass

import com.intellij.navigation.{NavigationItem, ChooseByNameContributor}
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import scala.collection.JavaConversions._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiClass

/**
 * Nikolay.Tropin
 * 12/19/13
 */
class ScalaGoToClassContributor extends ChooseByNameContributor {
  def getNames(project: Project, includeNonProjectItems: Boolean): Array[String] = {
    val classNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, project)
    val packageObjectNames = StubIndex.getInstance().getAllKeys(ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY, project)
    (classNames ++ packageObjectNames).toArray
  }

  def getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array[NavigationItem] = {
    val scope = if (includeNonProjectItems) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
    val classes = StubIndex.getInstance().safeGet(ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, name, project, scope, classOf[PsiClass])
    val packageObjects = StubIndex.getInstance().safeGet(ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY, name, project, scope, classOf[PsiClass])
    (classes ++ packageObjects).toArray
  }
}
