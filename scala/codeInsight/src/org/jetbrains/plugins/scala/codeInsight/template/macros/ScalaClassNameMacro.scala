package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

final class ScalaClassNameMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[PsiClass])).map{
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass.getName
      case cl: PsiClass => cl.getName
    }.map(new TextResult(_)).orNull
  }

  override def getPresentableName: String = CodeInsightBundle.message("macro.classname")
}
