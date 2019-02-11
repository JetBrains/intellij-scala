package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

case class Association(path: dependency.Path,
                       var range: TextRange)

object Association {

  implicit class AssociationExt(private val association: Association) extends AnyVal {

    def isSatisfiedIn(element: PsiElement): Boolean = element match {
      case reference: ScReference =>
        Dependency.dependencyFor(reference).exists {
          case Dependency(_, path) => path == association.path
          case _ => false
        }
      case _ => false
    }
  }

}
