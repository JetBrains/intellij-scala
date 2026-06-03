package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil

final class ScalaQualifiedClassNameMacro extends ScalaMacro {

  override def getNameShort: String = "qualifiedClassName"

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val parentClass = PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[PsiClass])
    Option(parentClass).map(_.getQualifiedName).map(new TextResult(_)).orNull
  }
}
