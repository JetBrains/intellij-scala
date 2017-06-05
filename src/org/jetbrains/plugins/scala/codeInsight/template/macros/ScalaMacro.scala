package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.{Expression, ExpressionContext, Macro, Result}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * @author adkozlov
  */
trait ScalaMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    innerCalculateResult(params, context)
  }

  override def calculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    innerCalculateLookupItems(params, context)
  }

  protected def innerCalculateResult(params: Array[Expression], context: ExpressionContext): Result

  protected def innerCalculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    super.calculateLookupItems(params, context)
  }

  private def getTypeSystem(context: ExpressionContext) = context.getProject.typeSystem
}
