package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaClassNameMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[PsiClass])).map{
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass.getName
      case cl: PsiClass => cl.getName
    }.map(new TextResult(_)).orNull
  }

  override def getName: String = MacroUtil.scalaIdPrefix + "className"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.classname")

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}
