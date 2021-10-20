package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.specialSupport

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaTypeConstants.Packages._
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext
import org.jetbrains.plugins.scala.project.ProjectContext

object OtherMethodsSpecialSupport {

  val CommonMethodsMapping: Map[(String, String), String] = Map(
    (s"$ScalaMath.abs", ScalaInt) -> s"$JavaLangMath.abs(0)",
    (s"$ScalaMath.abs", ScalaLong) -> s"$JavaLangMath.abs(0L)"
  )

  def psiMethodFromText(text: String)(implicit projectContext: ProjectContext): Option[PsiMethod] = {
    code"$text".asInstanceOf[MethodInvocation].target.map(_.element) match {
      case Some(method: PsiMethod) => Some(method)
      case _ => None
    }
  }
}
