package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor

/**
 * Pavel Fatin
 */

object MultipleInheritance extends AnnotatorPart[ScTemplateDefinition] {
  def kind: Class[ScTemplateDefinition] = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    AnnotatorPart.superRefs(definition).groupBy(_._2).foreach {
      case (Some(psiClass), entries) if isMixable(psiClass) && entries.size > 1 =>
        entries.map(_._1).foreach { refElement =>
          holder.createErrorAnnotation(refElement,
            "%s %s inherited multiple times".format(kindOf(psiClass), psiClass.name))
        }
      case _ =>
    }
  }
}