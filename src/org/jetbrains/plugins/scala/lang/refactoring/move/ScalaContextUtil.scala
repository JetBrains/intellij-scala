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
    for(reference <- scope.depthFirst.filterByType(classOf[ScReferenceElement]);
        dependency <- reference.getCopyableUserData(DEPENDENCY_KEY).asOptionOf[Dependency]) {
      dependency.restoreFor(reference)
      dependency.source.putCopyableUserData(DEPENDENCY_KEY, null)
    }
  }

  def encode(scope: PsiElement) {
    for (dependency <- Dependency.dependenciesIn(scope) if dependency.isExternal) {
      dependency.source.putCopyableUserData(DEPENDENCY_KEY, dependency)
    }
  }
}