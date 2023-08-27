package org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.specialSupport

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.Packages._
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext

object OtherMethodsSpecialSupport {

  val CommonMethodsMapping: Map[(String, String), String] = Map() ++
    unaryMathFunctionMappings("abs") ++
    unaryMathFunctionMappings("sqrt") ++
    binaryMathFunctionMappings("max") ++
    binaryMathFunctionMappings("min")

  private def unaryMathFunctionMappings(functionName: String): Seq[((String, String), String)] = List(
    (s"$ScalaMath.$functionName", ScalaInt) -> s"$JavaLangMath.$functionName(0)",
    (s"$ScalaMath.$functionName", ScalaLong) -> s"$JavaLangMath.$functionName(0L)",
    (s"$ScalaMath.$functionName", ScalaDouble) -> s"$JavaLangMath.$functionName(0.0)",
    (s"$ScalaMath.$functionName", ScalaFloat) -> s"$JavaLangMath.$functionName(0.0F)"
  )

  private def binaryMathFunctionMappings(functionName: String): Seq[((String, String), String)] = List(
    (s"$ScalaMath.$functionName", ScalaInt) -> s"$JavaLangMath.$functionName(0, 0)",
    (s"$ScalaMath.$functionName", ScalaLong) -> s"$JavaLangMath.$functionName(0L, 0L)",
    (s"$ScalaMath.$functionName", ScalaDouble) -> s"$JavaLangMath.$functionName(0.0, 0.0)",
    (s"$ScalaMath.$functionName", ScalaFloat) -> s"$JavaLangMath.$functionName(0.0F, 0.0F)"
  )

  def psiMethodFromText(text: String)(implicit project: Project): Option[PsiMethod] = {
    code"$text".asInstanceOf[MethodInvocation].target.map(_.element) match {
      case Some(method: PsiMethod) => Some(method)
      case _ => None
    }
  }
}
