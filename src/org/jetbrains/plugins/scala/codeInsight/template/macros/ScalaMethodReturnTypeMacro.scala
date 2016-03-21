package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * @author Roman.Shein
 * @since 24.09.2015.
 */
class ScalaMethodReturnTypeMacro extends ScalaMacro {
  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext)
                                   (implicit typeSystem: TypeSystem): Result = {
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[ScFunction])).
            map(_.getType(TypingContext.empty).getOrAny match {
              case ScFunctionType(rt, _) => rt
              case t => t
            }).map(new ScalaTypeResult(_)).orNull
  }

  override def getName: String = MacroUtil.scalaIdPrefix + "methodReturnType"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "methodReturnType()"

  override def getDefaultValue = "a"

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}
