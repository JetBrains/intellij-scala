package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTuple
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandTupleInstantiation extends AbstractTransformer {
  def transformation: PartialFunction[PsiElement, Unit] = {
    case (e @ ScTuple(exprs)) =>
      e.replace(code"Tuple${exprs.length}(${@@(exprs)})")
  }
}
