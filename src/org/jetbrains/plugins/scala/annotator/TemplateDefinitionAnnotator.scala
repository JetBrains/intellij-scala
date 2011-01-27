package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import lang.psi.api.expr.ScNewTemplateDefinition
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScTemplateDefinition}

/**
 * Pavel Fatin
 */

trait TemplateDefinitionAnnotator {
  def annotateTemplateDefinition(defintion: ScTemplateDefinition, holder: AnnotationHolder) {
    val block = defintion.extendsBlock

    val refs = block.templateParents.toSeq.flatMap(_.typeElements).map { refElement =>
       val psiClass = refElement.getType(TypingContext.empty).toOption.flatMap(ScType.extractClass(_))
      (refElement, psiClass)
    }

    refs.drop(1).foreach {
      case (refElement, Some(psiClass)) if !psiClass.isInstanceOf[ScTrait] =>
        holder.createErrorAnnotation(refElement, "Class %s needs to be trait to be mixed in".format(psiClass.getName))
      case _ =>
    }

    refs.groupBy(_._2).foreach {
      case (Some(psiClass: ScTrait), entries) if entries.size > 1 => entries.map(_._1).foreach { refElement =>
        holder.createErrorAnnotation(refElement, "Trait %s inherited multiple times".format(psiClass.getName))
      }
      case _ =>
    }

    if (defintion.isInstanceOf[ScNewTemplateDefinition] && block.templateBody.isEmpty) return

    refs.foreach {
      case (refElement, Some(psiClass)) if psiClass.getModifierList.hasModifierProperty("final") =>
        holder.createErrorAnnotation(refElement, "Illegal inheritance from final class %s".format(psiClass.getName))
      case (refElement, Some(psiClass: ScTypeDefinition)) if psiClass.getModifierList.hasModifierProperty("sealed") &&
              psiClass.getContainingFile != refElement.getContainingFile => {
        val entity = if(psiClass.isInstanceOf[ScTrait]) "trait" else "class"
        val message = "Illegal inheritance from sealed %s %s".format(entity, psiClass.getName)
        holder.createErrorAnnotation(refElement, message)
      }
      case _ =>
    }
  }
}