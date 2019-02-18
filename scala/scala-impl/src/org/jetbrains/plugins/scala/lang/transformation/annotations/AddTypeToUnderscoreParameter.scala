package org.jetbrains.plugins.scala.lang
package transformation
package annotations

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class AddTypeToUnderscoreParameter extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case (e: ScUnderscoreSection) && Typeable(t) if !e.nextSibling.exists(_.getText == ":") =>
      appendTypeAnnotation(t) { annotation =>
        val replacement = code"(_: $annotation)"
        val result = e.replace(replacement)
        result.getFirstChild.getNextSibling.getLastChild
      }
  }
}
