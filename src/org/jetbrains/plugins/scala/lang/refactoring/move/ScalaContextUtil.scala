package org.jetbrains.plugins.scala
package lang.refactoring.move

import extensions._
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import lang.psi.api.base.ScReferenceElement
import lang.dependency.Dependency

/**
 * Pavel Fatin
 */

object ScalaContextUtil {
  private val DEPENDENCY_KEY: Key[Dependency] = Key.create("DEPENDENCY")

  def decode(scope: PsiElement) {
    val dependencies = scope.depthFirst
            .filterByType(classOf[ScReferenceElement])
            .map(_.getCopyableUserData(DEPENDENCY_KEY))
            .filterByType(classOf[Dependency])
      
    dependencies.foreach(_.restore())
    
    dependencies.map(_.source).foreach(_.putCopyableUserData(DEPENDENCY_KEY, null))
  }

  def encode(scope: PsiElement) {
    Dependency.dependenciesIn(scope).filter(_.isExternal).foreach { dependency =>
      dependency.source.putCopyableUserData(DEPENDENCY_KEY, dependency)
    }
  }
}