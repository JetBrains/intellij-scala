package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Roman.Shein
 * @since 23.09.2015.
 */
class ScalaComponentTypeOfMacro extends ScalaMacro("macro.component.type.of.array") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    if (params.length != 1) return null
    params.head.calculateResult(context) match {
      case scTypeRes: ScalaTypeResult =>
        MacroUtil.getComponentFromArrayType(scTypeRes.myType).map(new ScalaTypeResult(_)).orNull
      case otherRes: Result =>
        MacroUtil.resultToScExpr(otherRes, context).flatMap(_.`type`().toOption).
                flatMap(MacroUtil.getComponentFromArrayType).map(new ScalaTypeResult(_)).orNull
    }
  }

  override def calculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    if (params.length != 1) return null
    val outerItems = params(0).calculateLookupItems(context)
    if (outerItems == null) return null

    outerItems.flatMap {
      case lookupItem: ScalaLookupItem => lookupItem.element match {
        case typeDef: ScTypeDefinition =>
          typeDef.`type`().toOption.flatMap(MacroUtil.getComponentFromArrayType).
                  map(MacroUtil.getTypeLookupItem(_, context.getProject))
        case _ => None
      }
      case _ => None
    }.filter(_.isDefined).map(_.get)
  }
}