package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

class CreateUnapplyQuickFix(clazz: ScTypeDefinition, pattern: ScPattern) extends CreateApplyOrUnapplyQuickFix(clazz) {
  override def getFamilyName: String = ScalaBundle.message("family.name.create.unapply.method")

  override def getText: String = ScalaBundle.message("create.unapply.method.in", clazz.shortDefinition)

  override protected def methodType: Some[String] = Some(unapplyMethodTypeText(pattern))

  override protected def methodText: String = unapplyMethodText(pattern)

  override protected def addElementsToTemplate(method: ScFunction, builder: TemplateBuilder): Unit = {
    addParametersToTemplate(method, builder)
    addUnapplyResultTypesToTemplate(method, builder)
    addQmarksToTemplate(method, builder)
  }
}
