package org.jetbrains.plugins.scala
package hierarchy


import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.`type`.JavaTypeHierarchyProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.collection.immutable.HashSet

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.06.2009
 */
class ScalaTypeHierarchyProvider extends JavaTypeHierarchyProvider {
  override def createHierarchyBrowser(target: PsiElement): HierarchyBrowser = {
    target match {
      case clazz: ScTypeDefinition =>
        collectSupers(clazz, new HashSet[ScTypeDefinition])
      case _ =>
    }
    super.createHierarchyBrowser(target)
  }

  def collectSupers(clazz: ScTypeDefinition, visited: HashSet[ScTypeDefinition]) {
    clazz.supers.foreach {
      case clazz: ScTypeDefinition =>
        if (visited.contains(clazz)) {
          println("clazz.getText = " + clazz.getText)
        } else {
          collectSupers(clazz, visited + clazz)
        }
      case _ =>
    }
  }
}