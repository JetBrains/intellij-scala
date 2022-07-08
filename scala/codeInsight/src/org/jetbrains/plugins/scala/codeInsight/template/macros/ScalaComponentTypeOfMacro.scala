package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass

final class ScalaComponentTypeOfMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params match {
    case Array(head) =>
      val maybeType = head.calculateResult(context) match {
        case ScalaTypeResult(scType) => Some(scType)
        case result => resultToScExpr(result)(context)
      }

      maybeType.flatMap(arrayComponent)
        .map(ScalaTypeResult)
        .orNull
    case _ => null
  }

  override def calculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = params match {
    case Array(head) =>
      head.calculateLookupItems(context) match {
        case null => null
        case outerItems =>
          outerItems.collect {
            case ScalaLookupItem(_, typeDefinition: ScTypeDefinition) => typeDefinition
          }.flatMap(_.`type`().toOption)
            .flatMap(arrayComponent)
            .collect {
              case ExtractClass(typeDefinition: ScTypeDefinition) => createLookupItem(typeDefinition)
            }
      }
    case _ => null
  }

  override def getPresentableName: String = CodeInsightBundle.message("macro.component.type.of.array")
}