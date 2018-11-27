package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt

/**
  * Pavel Fatin
  */

object IllegalInheritance extends TemplateDefinitionAnnotatorPart {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    if (!typeAware) return

    definition.selfTypeElement.flatMap(_.`type`().toOption).
      orElse(definition.`type`().toOption)
      .foreach { ownType =>

        collectSuperRefs(definition) {
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