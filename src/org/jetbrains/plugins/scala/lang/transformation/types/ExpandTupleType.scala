package org.jetbrains.plugins.scala.lang.transformation
package types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
class ExpandTupleType extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case e @ ScTupleTypeElement(elements @ _*)
      if !e.getParent.isInstanceOf[ScFunctionalTypeElement] =>

      e.replace(code"Tuple${elements.length}[${@@(elements)}]"(Type))
  }
}