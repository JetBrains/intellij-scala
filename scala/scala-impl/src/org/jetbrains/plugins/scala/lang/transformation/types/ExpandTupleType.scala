package org.jetbrains.plugins.scala.lang.transformation.types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.transformation.AbstractTransformer
import org.jetbrains.plugins.scala.project.ProjectContext

class ExpandTupleType extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScTupleTypeElement(elements @ _*)
      if !e.getParent.is[ScFunctionalTypeElement] =>

      e.replace(code"Tuple${elements.length}[${@@(elements)}]"(Type))
  }
}
