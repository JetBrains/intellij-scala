package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScNamedTupleComponent
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNamedTuple

import scala.collection.mutable

object ScNamedTupleAnnotator extends ElementAnnotator[ScNamedTuple] {
  override def annotate(element: ScNamedTuple, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    annotateComponents(element.components)
  }

  def annotateComponents(comps: Seq[ScNamedTupleComponent])(implicit holder: ScalaAnnotationHolder): Unit = {
    val seen = mutable.Set.empty[String]

    for (comp <- comps) {
      val name = comp.name
      if (!seen.add(name)) {
        holder.createErrorAnnotation(
          comp.nameElement.getOrElse(comp),
          ScalaBundle.message("duplicate.name.in.named.tuple.name", name)
        )
      }
    }
  }
}
