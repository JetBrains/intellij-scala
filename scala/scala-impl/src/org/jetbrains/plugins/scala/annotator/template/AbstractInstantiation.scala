package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
object AbstractInstantiation extends TemplateDefinitionAnnotatorPart {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    val newObject = definition.isInstanceOf[ScNewTemplateDefinition]
    val hasBody = definition.extendsBlock.templateBody.isDefined
    val hasEarlyBody = definition.extendsBlock.earlyDefinitions.exists(_.members.nonEmpty)

    if (!newObject || hasEarlyBody || hasBody) return

    superRefs(definition) match {
      case (range, clazz) :: Nil if isAbstract(clazz) =>
        val message = ScalaBundle.message("illegal.instantiation", kindOf(clazz), clazz.name)
        holder.createErrorAnnotation(range, message)
      case _ =>
    }
  }
}