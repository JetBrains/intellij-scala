package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.template.{collectSuperRefs, kindOf, superRefs}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType

trait ScTemplateDefinitionAnnotator extends Annotatable { self: ScTemplateDefinition =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    annotateFinalClassInheritance(holder)

    if (typeAware) {
      annotateIllegalInheritance(holder)
    }
  }

  private def annotateFinalClassInheritance(holder: AnnotationHolder): Unit = {
    val newInstance = isInstanceOf[ScNewTemplateDefinition]
    val hasBody = extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    superRefs(this).collect {
      case (range, clazz) if clazz.hasFinalModifier =>
        (range, ScalaBundle.message("illegal.inheritance.from.final.kind", kindOf(clazz, toLowerCase = true), clazz.name))
      case (range, clazz) if ValueClassType.extendsAnyVal(clazz) =>
        (range, ScalaBundle.message("illegal.inheritance.from.value.class", clazz.name))
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message)
    }
  }

  private def annotateIllegalInheritance(holder: AnnotationHolder): Unit = {
    selfTypeElement.flatMap(_.`type`().toOption).
      orElse(`type`().toOption)
      .foreach { ownType =>

        collectSuperRefs(this) {
          _.extractClassType
        }.foreach {
          case (range, (clazz: ScTemplateDefinition, substitutor)) =>
            clazz.selfType.filterNot { selfType =>
              ownType.conforms(substitutor(selfType))
            }.foreach { selfType =>
              holder.createErrorAnnotation(range, ScalaBundle.message("illegal.inheritance.self.type", ownType.presentableText, selfType.presentableText))
            }
          case _ =>
        }
      }
  }
}
