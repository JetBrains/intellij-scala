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
object FinalClassInheritance extends AnnotatorPart[ScTemplateDefinition] {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    val newInstance = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    superRefs(definition).collect {
      case (reference, clazz) if clazz.hasFinalModifier =>
        (reference, ScalaBundle.message("illegal.inheritance.from.final.kind", kindOf(clazz, toLowerCase = true), clazz.name))
      case (reference, clazz) if ValueClassType.extendsAnyVal(clazz) =>
        (reference, ScalaBundle.message("illegal.inheritance.from.value.class", clazz.name))
    }.foreach {
      case (reference, message) =>
        holder.createErrorAnnotation(reference, message)
    }
  }
}