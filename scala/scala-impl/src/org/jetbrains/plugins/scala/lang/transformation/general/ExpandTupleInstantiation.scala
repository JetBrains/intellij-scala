package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTuple
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandTupleInstantiation extends AbstractTransformer {
  protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e @ ScTuple(exprs) =>
      e.replace(code"Tuple${exprs.length}(${@@(exprs)})")
  }
}
