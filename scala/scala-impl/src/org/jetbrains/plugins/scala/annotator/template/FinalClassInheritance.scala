package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType

/**
  * Pavel Fatin
  */
object FinalClassInheritance extends TemplateDefinitionAnnotatorPart {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    val newInstance = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    superRefs(definition).collect {
      case (range, clazz) if clazz.hasFinalModifier =>
        (range, ScalaBundle.message("illegal.inheritance.from.final.kind", kindOf(clazz, toLowerCase = true), clazz.name))
      case (range, clazz) if ValueClassType.extendsAnyVal(clazz) =>
        (range, ScalaBundle.message("illegal.inheritance.from.value.class", clazz.name))
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message)
    }
  }
}