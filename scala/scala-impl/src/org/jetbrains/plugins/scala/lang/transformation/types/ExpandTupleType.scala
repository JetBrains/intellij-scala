package org.jetbrains.plugins.scala.lang.transformation
package types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

class ExpandTupleType extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScTupleTypeElement(elements @ _*)
      if !e.getParent.isInstanceOf[ScFunctionalTypeElement] =>

      e.replace(code"Tuple${elements.length}[${@@(elements)}]"(Type))
  }
}