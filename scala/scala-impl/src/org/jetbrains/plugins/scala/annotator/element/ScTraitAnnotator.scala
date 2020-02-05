package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait

object ScTraitAnnotator extends ElementAnnotator[ScTrait] {
  override def annotate(element: ScTrait, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    for {
      constructor <- element.constructor
      if !constructor.isInScala3Module
    } {
      holder.createErrorAnnotation(constructor, ScalaBundle.message("trait.parameter.require.scala3"))
    }
}
