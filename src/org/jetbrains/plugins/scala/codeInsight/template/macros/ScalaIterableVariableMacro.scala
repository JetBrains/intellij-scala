package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable.ArrayBuffer

/**
 * @author Roman.Shein
 * @since 24.09.2015.
 */
class ScalaIterableVariableMacro extends ScalaVariableOfTypeMacro {

  override def getName: String = MacroUtil.scalaIdPrefix + "iterableVariable"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.iterable.variable")

  override def addLookupItems(exprs: Array[String],
                              context: ExpressionContext,
                              variant: ScalaResolveResult,
                              scType: ScType,
                              project: Project,
                              array: ArrayBuffer[LookupElement])
                             (implicit typeSystem: TypeSystem) =
    super.addLookupItems(Array(ScalaVariableOfTypeMacro.iterableId), context, variant, scType, project, array)

  override def getResult(exprs: Array[Expression],
                         context: ExpressionContext,
                         variant: ScalaResolveResult,
                         scType: ScType,
                         project: Project)
                        (implicit typeSystem: TypeSystem): Option[Result] =
    super.getResult(Array(new TextExpression(ScalaVariableOfTypeMacro.iterableId)), context, variant, scType, project)

  override def validExprsCount(exprsCount: Int): Boolean = exprsCount == 0
}
