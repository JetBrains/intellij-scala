package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScFunctionDeclaration, ScTypeAliasDeclaration}

/**
 * Pavel Fatin
 */

object UndefinedMember extends AnnotatorPart[ScTemplateDefinition] {
  val Message = "Only classes can have declared but undefined members"

  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, advanced: Boolean) {
    val isNew = definition.isInstanceOf[ScNewTemplateDefinition]
    val isObject = definition.isInstanceOf[ScObject]

    if (!isNew && !isObject) return

    definition.members.foreach {
      case declaration: ScDeclaration => holder.createErrorAnnotation(declaration, Message)
      case _ =>
    }
  }
}