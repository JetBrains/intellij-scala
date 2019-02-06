package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.project._

trait ScLiteralTypeElementAnnotator extends Annotatable { self: ScLiteralTypeElement =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    if (!holder.getCurrentAnnotationSession.getFile.literalTypesEnabled) {
      holder.createErrorAnnotation(this, ScalaBundle.message("wrong.type.no.literal.types", getText))
    }
  }
}
