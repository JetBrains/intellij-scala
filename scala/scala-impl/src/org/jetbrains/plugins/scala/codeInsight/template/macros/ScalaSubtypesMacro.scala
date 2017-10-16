package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

import scala.collection.JavaConverters._

/**
 * @author Roman.Shein
 * @since 29.09.2015.
 */
class ScalaSubtypesMacro extends ScalaMacro {
  override def getName: String = MacroUtil.scalaIdPrefix + "subtypes"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "subtypes(TYPE)"

  override def getDefaultValue = "a"

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]

  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext): Result =
    if (params.length != 1) null else params(0).calculateResult(context)

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def innerCalculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    if (params.length != 1) return Array[LookupElement]()
    val project = context.getProject
    params(0).calculateResult(context) match {
      case scTypeRes: ScalaTypeResult =>
        scTypeRes.myType.extractClass match {
          case Some(x: ScTypeDefinition) =>
            ClassInheritorsSearch.search(x, GlobalSearchScope.projectScope(context.getProject), true)
              .findAll().asScala
              .collect {
                case definition: ScTypeDefinition => definition
              }
              .flatMap(_.getType().toOption)
              .flatMap(MacroUtil.getTypeLookupItem(_, project))
              .toArray
          case _ => Array[LookupElement]()
        }
      case _ => Array[LookupElement]()
    }
  }
}
