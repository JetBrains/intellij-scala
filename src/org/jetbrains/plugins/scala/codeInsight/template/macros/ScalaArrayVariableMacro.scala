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
class ScalaArrayVariableMacro extends ScalaVariableOfTypeMacro {
  override def getName = MacroUtil.scalaIdPrefix + "arrayVariable"

  override def getPresentableName = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.array.variable")

  override def addLookupItems(exprs: Array[String],
                              context: ExpressionContext,
                              variant: ScalaResolveResult,
                              scType: ScType,
                              project: Project,
                              array: ArrayBuffer[LookupElement])
                             (implicit typeSystem: TypeSystem) =
    super.addLookupItems(Array("scala.Array"), context, variant, scType, project, array)

  override def getResult(exprs: Array[Expression],
                         context: ExpressionContext,
                         variant: ScalaResolveResult,
                         scType: ScType,
                         project: Project)
                        (implicit typeSystem: TypeSystem): Option[Result] =
    super.getResult(Array(new TextExpression("scala.Array")), context, variant, scType, project)

  override def validExprsCount(exprsCount: Int): Boolean = exprsCount == 0
}
