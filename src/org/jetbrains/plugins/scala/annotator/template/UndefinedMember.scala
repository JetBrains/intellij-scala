package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScAnnotationsHolder, ScDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}

/**
 * Pavel Fatin
 */

object UndefinedMember extends AnnotatorPart[ScTemplateDefinition] {
  val Message = "Only classes can have declared but undefined members"

  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    val isNew = definition.isInstanceOf[ScNewTemplateDefinition]
    val isObject = definition.isInstanceOf[ScObject]

    if (!isNew && !isObject) return

    definition.members.foreach {
      case declaration: ScDeclaration => 
        val isNative = declaration match {
          case a: ScAnnotationsHolder => a.hasAnnotation("scala.native") != None
          case _ => false
        }
        if (!isNative) holder.createErrorAnnotation(declaration, Message)
      case _ =>
    }
  }
}