package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/**
 * Pavel Fatin
 */

object SealedClassInheritance extends AnnotatorPart[ScTemplateDefinition] {
  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    definition.containingScalaFile match {
      case Some(a) if !a.isCompiled =>
      case _ => return
    }
    val newInstance = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    AnnotatorPart.superRefs(definition).foreach {
      case (refElement, Some(psiClass: ScTypeDefinition)) if psiClass.hasModifierProperty("sealed") &&
        psiClass.getContainingFile.getNavigationElement != refElement.getContainingFile.getNavigationElement =>
        holder.createErrorAnnotation(refElement,
          "Illegal inheritance from sealed %s %s".format(kindOf(psiClass).toLowerCase, psiClass.name))
      case _ =>
    }
  }
}