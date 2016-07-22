package org.jetbrains.plugins.scala.lang.transformation
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScParenthesisedTypeElement, ScTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
class ExpandFunctionType extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case e @ ScFunctionalTypeElement(l, Some(r)) =>
      val elements = l match {
        case ScParenthesisedTypeElement(element) => Seq(element)
        case ScTupleTypeElement(elements @ _*) => elements
        case element => Seq(element)
      }
      e.replace(code"Function${elements.length}[${@@(elements)}, $r]"(Type))
  }
}
