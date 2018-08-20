package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

import scala.collection.JavaConverters

/**
  * @author Roman.Shein
  * @since 29.09.2015.
  */
class ScalaSubtypesMacro extends ScalaMacro("macro.subtypes") {

  override def calculateResult(expressions: Array[Expression], context: ExpressionContext): Result =
    expressions match {
      case Array(head) => head.calculateResult(context)
      case _ => null
    }

  override def calculateQuickResult(expressions: Array[Expression], context: ExpressionContext): Result =
    calculateResult(expressions, context)

  override def calculateLookupItems(expressions: Array[Expression], context: ExpressionContext): Array[LookupElement] =
    calculateResult(expressions, context) match {
      case scTypeRes: ScalaTypeResult =>
        scTypeRes.myType.extractClass match {
          case Some(typeDefinition: ScTypeDefinition) =>
            import JavaConverters._
            val inheritors = ClassInheritorsSearch.search(
              typeDefinition,
              GlobalSearchScope.projectScope(context.getProject),
              true
            ).findAll().asScala

            inheritors.collect {
              case definition: ScTypeDefinition => createLookupItem(definition)
            }.toArray
          case _ => LookupElement.EMPTY_ARRAY
        }
      case _ => LookupElement.EMPTY_ARRAY
    }

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override protected def message(nameKey: String): String = ScalaMacro.message(nameKey)
}
