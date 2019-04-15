package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.template.{isAbstract, kindOf, superRefs}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition

trait ScNewTemplateDefinitionAnnotator extends Annotatable { self: ScNewTemplateDefinition =>

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    annotateAbstractInstantiation(holder)
  }

  // TODO package private
  def annotateAbstractInstantiation(holder: AnnotationHolder): Unit = {
    val hasBody = extendsBlock.templateBody.isDefined
    val hasEarlyBody = extendsBlock.earlyDefinitions.exists(_.members.nonEmpty)

    if (hasEarlyBody || hasBody) return

    superRefs(this) match {
      case (range, clazz) :: Nil if isAbstract(clazz) =>
        val message = ScalaBundle.message("illegal.instantiation", kindOf(clazz), clazz.name)
        holder.createErrorAnnotation(range, message)
      case _ =>
    }
  }
}
