package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

/**
 * Pavel Fatin
 */

class CreateApplyQuickFix(td: ScTypeDefinition, call: ScMethodCall)
        extends {val getFamilyName = "Create 'apply' method"} with CreateApplyOrUnapplyQuickFix(td) {

  val methodType = call.expectedType().map(_.canonicalText)

  val methodText = {
    val argsText = CreateFromUsageUtil.paramsText(call.argumentExpressions)
    val dummyTypeText = methodType.fold("")(_ => ": Int")
    s"def apply$argsText$dummyTypeText = ???"
  }

  override protected def addElementsToTemplate(method: ScFunction, builder: TemplateBuilder) = {
    for (aType <- methodType;
         typeElement <- method.children.findByType(classOf[ScSimpleTypeElement])) {
      builder.replaceElement(typeElement, aType)
    }

    addParametersToTemplate(method, builder)
    addQmarksToTemplate(method, builder)
  }
}