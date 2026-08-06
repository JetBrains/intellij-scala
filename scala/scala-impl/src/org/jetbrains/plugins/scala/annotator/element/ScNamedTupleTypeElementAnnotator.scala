package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScNamedTupleTypeElement

object ScNamedTupleTypeElementAnnotator extends ElementAnnotator[ScNamedTupleTypeElement] {
  override def annotate(element: ScNamedTupleTypeElement, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    ScNamedTupleAnnotator.annotateComponents(element.components)
  }
}
