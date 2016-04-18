package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.{Expression, ExpressionContext, Macro, Result}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * @author adkozlov
  */
trait ScalaMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext) = {
    innerCalculateResult(params, context)
  }

  override def calculateLookupItems(params: Array[Expression], context: ExpressionContext) = {
    innerCalculateLookupItems(params, context)
  }

  protected def innerCalculateResult(params: Array[Expression], context: ExpressionContext)
                                    (implicit typeSystem: TypeSystem = getTypeSystem(context)): Result

  protected def innerCalculateLookupItems(params: Array[Expression], context: ExpressionContext)
                                         (implicit typeSystem: TypeSystem = getTypeSystem(context)): Array[LookupElement] = {
    super.calculateLookupItems(params, context)
  }

  private def getTypeSystem(context: ExpressionContext) = context.getProject.typeSystem
}
