package org.jetbrains.plugins.scala.lang.transformation
package calls

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandApplyCall extends AbstractTransformer {
  def transformation: PartialFunction[PsiElement, Unit] = {
    case ScMethodCall(e @ RenamedReference(_, "apply"), _) =>
      e.replace(code"$e.apply")
  }
}
