package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
 * @author Roman.Shein
 * @since 24.09.2015.
 */
class ScalaQualifiedClassNameMacro extends Macro {
  override def getName: String = MacroUtil.scalaIdPrefix + "qualifiedClassName"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.qualified.class.name")

  override def getDefaultValue = "a"

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[PsiClass])).map(_.getQualifiedName).
            map(new TextResult(_)).orNull
  }
}
