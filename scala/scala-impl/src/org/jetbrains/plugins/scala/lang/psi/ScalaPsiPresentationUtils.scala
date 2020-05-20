package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi._
import com.intellij.psi.util.{PsiFormatUtil, PsiFormatUtilBase}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation._

// TODO 2: unify with org.jetbrains.plugins.scala.lang.psi.PresentationUtil
// TODO 4: unify with org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
// TODO 5: unify with com.intellij.psi.util.PsiFormatUtil
object ScalaPsiPresentationUtils {

  def methodPresentableText(
    method: PsiMethod,
  ): String =
    method match {
      case function: ScFunction =>
        FunctionRenderer.simple(_.presentableText(function)).render(function)
      case _ =>
        import PsiFormatUtilBase._
        val pramOptions = SHOW_NAME | SHOW_TYPE | TYPE_AFTER
        PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, pramOptions | SHOW_PARAMETERS, pramOptions)
    }
}
