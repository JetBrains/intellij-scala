package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * @author Roman.Shein
 * @since 23.09.2015.
 */
class ScalaComponentTypeOfMacro extends ScalaMacro {
  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext)
                                   (implicit typeSystem: TypeSystem): Result = {
    if (params.length != 1) return null
    params.head.calculateResult(context) match {
      case scTypeRes: ScalaTypeResult =>
        MacroUtil.getComponentFromArrayType(scTypeRes.myType).map(new ScalaTypeResult(_)).orNull
      case otherRes: Result =>
        MacroUtil.resultToScExpr(otherRes, context).flatMap(_.getType().toOption).
                flatMap(MacroUtil.getComponentFromArrayType).map(new ScalaTypeResult(_)).orNull
    }
  }

  override def innerCalculateLookupItems(params: Array[Expression], context: ExpressionContext)
                                        (implicit typeSystem: TypeSystem): Array[LookupElement] = {
    if (params.length != 1) return null
    val outerItems = params(0).calculateLookupItems(context)
    if (outerItems == null) return null

    outerItems.flatMap {
      case lookupItem: ScalaLookupItem => lookupItem.element match {
        case typeDef: ScTypeDefinition =>
          typeDef.getType(TypingContext.empty).toOption.flatMap(MacroUtil.getComponentFromArrayType).
                  map(MacroUtil.getTypeLookupItem(_, context.getProject))
        case _ => None
      }
      case _ => None
    }.filter(_.isDefined).map(_.get)
  }

  def getName: String = MacroUtil.scalaIdPrefix + "componentTypeOf"

  def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.component.type.of.array")

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}