package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}

/**
 * Pavel Fatin
 */

object MultipleInheritance extends AnnotatorPart[ScTemplateDefinition] {
  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    definition.refs.groupBy(_._2).foreach {
      case (Some(psiClass), entries) if isMixable(psiClass) && entries.size > 1 =>
        entries.map(_._1).foreach { refElement =>
          holder.createErrorAnnotation(refElement,
            "%s %s inherited multiple times".format(kindOf(psiClass), psiClass.getName))
        }
      case _ =>
    }
  }
}