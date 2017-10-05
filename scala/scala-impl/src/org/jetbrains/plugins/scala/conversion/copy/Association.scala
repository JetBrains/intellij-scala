package org.jetbrains.plugins.scala
package conversion.copy

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.conversion.ast.IntermediateNode
import org.jetbrains.plugins.scala.lang.dependency.{Dependency, DependencyKind, Path}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * Pavel Fatin
 */

case class Association(kind: DependencyKind, var range: TextRange, path: Path) {
  def isSatisfiedIn(element: PsiElement): Boolean =
    element match {
      case reference: ScReferenceElement =>
        Dependency.dependencyFor(reference).exists(it => it.kind == kind && it.path == path)
      case _ => false
    }
}


case class AssociationHelper(kind: DependencyKind, itype: IntermediateNode, path: Path)