package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

/**
  * Pavel Fatin
  */
object SealedClassInheritance extends AnnotatorPart[ScTemplateDefinition] {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    definition.containingScalaFile match {
      case Some(a) if !a.isCompiled =>
      case _ => return
    }
    val newInstance = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    superRefs(definition).foreach {
      case (refElement, definition: ScTypeDefinition) if definition.isSealed &&
        definition.getContainingFile.getNavigationElement != refElement.getContainingFile.getNavigationElement =>
        holder.createErrorAnnotation(
          refElement,
          s"Illegal inheritance from sealed ${kindOf(definition).toLowerCase} ${definition.name}"
        )
      case _ =>
    }
  }
}