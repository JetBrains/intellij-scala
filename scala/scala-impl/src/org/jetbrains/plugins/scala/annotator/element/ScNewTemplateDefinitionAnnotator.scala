package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.template.{isAbstract, kindOf, superRefs}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition

object ScNewTemplateDefinitionAnnotator extends ElementAnnotator[ScNewTemplateDefinition] {

  override def annotate(element: ScNewTemplateDefinition, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    annotateAbstractInstantiation(element)
  }

  // TODO package private
  def annotateAbstractInstantiation(element: ScNewTemplateDefinition)
                                   (implicit holder: ScalaAnnotationHolder): Unit = {
    val hasBody = element.extendsBlock.templateBody.isDefined
    val hasEarlyBody = element.extendsBlock.earlyDefinitions.exists(_.members.nonEmpty)

    if (hasEarlyBody || hasBody) return

    superRefs(element) match {
      case (range, clazz) :: Nil if isAbstract(clazz) =>
        val message = ScalaBundle.message("illegal.instantiation", kindOf(clazz), clazz.name)
        holder.createErrorAnnotation(range, message)
      case _ =>
    }
  }
}
