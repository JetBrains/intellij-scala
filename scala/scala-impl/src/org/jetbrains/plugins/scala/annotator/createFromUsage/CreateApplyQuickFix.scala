package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class CreateApplyQuickFix(td: ScTypeDefinition, call: ScMethodCall)
  extends CreateApplyOrUnapplyQuickFix(td) {

  override def getFamilyName: String = ScalaBundle.message("family.name.create.apply.method")

  override def getText: String = ScalaBundle.message("create.apply.method.in", td.shortDefinition)

  override val methodType: Option[String] = call.expectedType().map(_.canonicalText)

  override val methodText: String = {
    val argsText = CreateFromUsageUtil.paramsText(call.argumentExpressions)
    val dummyTypeText = methodType.fold("")(_ => ": Int")
    s"def apply$argsText$dummyTypeText = ???"
  }

  override protected def addElementsToTemplate(method: ScFunction, builder: TemplateBuilder): Unit = {
    for (aType <- methodType;
         typeElement <- method.children.findByType[ScSimpleTypeElement]) {
      builder.replaceElement(typeElement, aType)
    }

    addParametersToTemplate(method, builder)
    addQmarksToTemplate(method, builder)
  }
}