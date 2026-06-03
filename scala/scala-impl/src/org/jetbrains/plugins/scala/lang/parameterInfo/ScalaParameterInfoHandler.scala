package org.jetbrains.plugins.scala.lang.parameterInfo

import com.intellij.lang.parameterInfo.{CreateParameterInfoContext, ParameterInfoContext, ParameterInfoHandlerWithTabActionSupport, UpdateParameterInfoContext}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

abstract class ScalaParameterInfoHandler[
  ParameterOwner <: PsiElement,
  ParameterType,
  ActualParameterType <: PsiElement
] extends ParameterInfoHandlerWithTabActionSupport[ParameterOwner, ParameterType, ActualParameterType] {

  protected def findCall(context: ParameterInfoContext): ParameterOwner

  override def findElementForParameterInfo(context: CreateParameterInfoContext): ParameterOwner =
    findCall(context)

  override def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ParameterOwner =
    findCall(context)

  override def showParameterInfo(element: ParameterOwner, context: CreateParameterInfoContext): Unit =
    context.showHint(element, element.getTextRange.getStartOffset, this)

  override def updateParameterInfo(parameterOwner: ParameterOwner, context: UpdateParameterInfoContext): Unit = {
    if (context.getParameterOwner != parameterOwner) {
      context.removeHint()
    }
    val offset = context.getOffset
    var child = parameterOwner.getNode.getFirstChildNode
    var parameterIndex = 0
    while (child != null && child.getStartOffset < offset) {
      if (child.getElementType eq ScalaTokenTypes.tCOMMA) {
        parameterIndex = parameterIndex + 1
      }
      child = child.getTreeNext
    }
    context.setCurrentParameter(parameterIndex)
  }
}