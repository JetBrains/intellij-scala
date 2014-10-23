package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Pavel Fatin
 */

object FinalClassInheritance extends AnnotatorPart[ScTemplateDefinition] {
  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    val newInstance = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    definition.refs.foreach {
      case (refElement, Some((psiClass, _))) if psiClass.hasFinalModifier =>
        holder.createErrorAnnotation(refElement,
          "Illegal inheritance from final %s %s".format(kindOf(psiClass).toLowerCase, psiClass.name))
      case _ =>
    }
  }
}