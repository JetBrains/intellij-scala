package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.template.TemplateBuilder
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * Nikolay.Tropin
 * 2014-08-01
 */
class CreateUnapplyQuickFix(clazz: ScTypeDefinition, pattern: ScPattern)
        extends {val getFamilyName = "Create 'unapply' method"} with CreateApplyOrUnapplyQuickFix(clazz) {

  override protected def methodType: Some[String] = Some(unapplyMethodTypeText(pattern))

  override protected def methodText = unapplyMethodText(pattern)

  override protected def addElementsToTemplate(method: ScFunction, builder: TemplateBuilder) = {
    addParametersToTemplate(method, builder)
    addUnapplyResultTypesToTemplate(method, builder)
    addQmarksToTemplate(method, builder)
  }
}
