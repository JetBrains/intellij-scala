package org.jetbrains.plugins.scala
package conversion.copy

import lang.dependency.{Dependency, DependencyKind, Path}
import com.intellij.openapi.util.TextRange
import lang.psi.api.base.ScReferenceElement
import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

case class Association(kind: DependencyKind, range: TextRange, path: Path) {
  def isSatisfiedIn(element: PsiElement): Boolean =
    element match {
      case reference: ScReferenceElement =>
        Dependency.dependencyFor(reference).exists(it => it.kind == kind && it.path == path)
      case _ => false
    }
}