package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import lang.psi.api.expr.ScNewTemplateDefinition
import overrideImplement.ScalaOIUtil
import lang.psi.types.{PhysicalSignature, ScType}
import com.intellij.psi.{PsiClass, PsiElement}
import lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition, ScTrait, ScTemplateDefinition}

/**
 * Pavel Fatin
 */

trait TemplateDefinitionAnnotator {
  def annotateTemplateDefinition(defintion: ScTemplateDefinition, holder: AnnotationHolder) {
    def error(e: PsiElement, format: String, args: Any*) =
      holder.createErrorAnnotation(e, format.format(args: _*))

    val block = defintion.extendsBlock

    val refs = block.templateParents.toSeq.flatMap(_.typeElements).map { refElement =>
       val psiClass = refElement.getType(TypingContext.empty).toOption.flatMap(ScType.extractClass(_))
      (refElement, psiClass)
    }

    val newWithoutBody = defintion.isInstanceOf[ScNewTemplateDefinition] && block.templateBody.isEmpty

    refs.headOption.foreach {
      case (refElement, Some(psiClass)) => {
        if(newWithoutBody && isAbstract(psiClass))
          error(refElement, "%s %s is abstract; cannot be instantiated", kindOf(psiClass), psiClass.getName)
      }
      case _ =>
    }

    refs.drop(1).foreach {
      case (refElement, Some(psiClass)) if !isMixable(psiClass) =>
        error(refElement, "%s %s needs to be trait to be mixed in", kindOf(psiClass), psiClass.getName)
      case _ =>
    }

    refs.groupBy(_._2).foreach {
      case (Some(psiClass: ScTrait), entries) if entries.size > 1 => entries.map(_._1).foreach { refElement =>
        error(refElement, "%s %s inherited multiple times", kindOf(psiClass), psiClass.getName)
      }
      case _ =>
    }

    if (newWithoutBody) return

    refs.foreach {
      case (refElement, Some(psiClass)) if psiClass.getModifierList.hasModifierProperty("final") =>
        error(refElement, "Illegal inheritance from final %s %s", kindOf(psiClass).toLowerCase, psiClass.getName)
      case (refElement, Some(psiClass: ScTypeDefinition)) if psiClass.getModifierList.hasModifierProperty("sealed") &&
              psiClass.getContainingFile != refElement.getContainingFile => {
        error(refElement, "Illegal inheritance from sealed %s %s", kindOf(psiClass).toLowerCase, psiClass.getName)
      }
      case _ =>
    }
  }

  private def kindOf(entity: PsiClass) = entity match {
    case _: ScTrait => "Trait"
    case _: ScObject => "Object"
    case c: PsiClass if c.isEnum => "Enum"
    case c: PsiClass if c.isInterface => "Interface"
    case _ => "Class"
  }

  private def isMixable(entity: PsiClass) = entity match {
    case _: ScTrait => true
    case c: PsiClass if c.isInterface => true
    case _ => false
  }

  private def isAbstract(entity: PsiClass) = entity match {
    case _: ScTrait => true
    case c: PsiClass if c.isInterface => true
    case c: PsiClass if c.hasModifierProperty("abstract") => true
    case _ => false
  }
}