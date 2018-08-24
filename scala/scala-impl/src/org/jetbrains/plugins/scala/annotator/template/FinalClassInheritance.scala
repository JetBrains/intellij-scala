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

    superRefs(definition).foreach {
      case (refElement, psiClass) if psiClass.hasFinalModifier =>
        holder.createErrorAnnotation(
          refElement,
          s"Illegal inheritance from final ${kindOf(psiClass).toLowerCase} ${psiClass.name}"
        )
      case (refElement, cl) if ValueClassType.extendsAnyVal(cl) =>
        holder.createErrorAnnotation(
          refElement,
          ScalaBundle.message("illegal.inheritance.from.value.class", cl.name)
        )
      case _ =>
    }
  }
}