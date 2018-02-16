package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * Pavel Fatin
 */

object NeedsToBeTrait extends AnnotatorPart[ScTemplateDefinition] {
  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    AnnotatorPart.superRefs(definition).drop(1).foreach {
      case (refElement, Some(psiClass)) if !isMixable(psiClass) =>
        holder.createErrorAnnotation(refElement,
          "%s %s needs to be trait to be mixed in".format(kindOf(psiClass), psiClass.name))
      case _ =>
    }
  }
}