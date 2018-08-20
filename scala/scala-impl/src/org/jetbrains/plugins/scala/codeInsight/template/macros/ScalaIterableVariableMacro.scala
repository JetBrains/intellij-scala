package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer

/**
 * @author Roman.Shein
 * @since 24.09.2015.
 */
class ScalaIterableVariableMacro extends ScalaVariableOfTypeMacroBase("macro.iterable.variable") {

  override def addLookupItems(exprs: Array[String],
                              context: ExpressionContext,
                              variant: ScalaResolveResult,
                              scType: ScType,
                              project: Project,
                              array: ArrayBuffer[LookupElement]): Unit =
    super.addLookupItems(Array(ScalaVariableOfTypeMacro.iterableId), context, variant, scType, project, array)

  override def getResult(exprs: Array[Expression],
                         context: ExpressionContext,
                         variant: ScalaResolveResult,
                         scType: ScType,
                         project: Project): Option[Result] =
    super.getResult(Array(new TextExpression(ScalaVariableOfTypeMacro.iterableId)), context, variant, scType, project)

  override def arrayIsValid(array: Array[_]): Boolean = !super.arrayIsValid(array)
}
