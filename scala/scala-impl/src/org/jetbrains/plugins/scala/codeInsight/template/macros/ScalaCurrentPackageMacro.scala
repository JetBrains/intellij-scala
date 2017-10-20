package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaCurrentPackageMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    PsiDocumentManager.getInstance(context.getProject).getPsiFile(context.getEditor.getDocument) match {
      case scFile: ScalaFile => new TextResult(scFile.getPackageName)
      case _ => new TextResult("")
    }
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getName: String = MacroUtil.scalaIdPrefix + "currentPackage"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.current.package")

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}
