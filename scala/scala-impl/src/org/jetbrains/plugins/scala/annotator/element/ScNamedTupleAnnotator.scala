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

  private val invalidNumberedComponentNameRegex = raw"_\d+".r

  def annotateComponents(comps: Seq[ScNamedTupleComponent])(implicit holder: ScalaAnnotationHolder): Unit = {
    val seen = mutable.Set.empty[String]

    for (comp <- comps) {
      lazy val nameElement = comp.nameElement.getOrElse(comp)
      val name = comp.name
      if (!seen.add(name)) {
        holder.createErrorAnnotation(
          nameElement,
          ScalaBundle.message("duplicate.name.in.named.tuple.name", name)
        )
      }

      if (invalidNumberedComponentNameRegex.matches(name)) {
        holder.createErrorAnnotation(
          nameElement,
          ScalaBundle.message("name.cannot.be.used.as.the.name.of.a.tuple.element", name)
        )
      }
    }
  }
}
