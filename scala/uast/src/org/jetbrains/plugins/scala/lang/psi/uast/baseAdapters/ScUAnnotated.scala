package org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters

import java.util

import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.uast.utils.JavaCollectionsCommon
import org.jetbrains.plugins.scala.lang.psi.uast.converter.BaseScala2UastConverter._
import org.jetbrains.plugins.scala.lang.psi.uast.{declarations, expressions}
import org.jetbrains.uast.{UAnnotated, UAnnotation}

import scala.collection.JavaConverters._

/**
  * Scala adapter of the [[UAnnotated]].
  * Provides:
  *  - default implementations based on `sourcePsi`
  *
  * @note Just handy util - it is not obligatory to be mixed in by according ScU*** elements.
  * @example inherited by some ScU*** elements in [[declarations]] and [[expressions]]
  */
trait ScUAnnotated extends UAnnotated {

  override def getUAnnotations: util.List[UAnnotation] = {
    val annotatedElement = getSourcePsi match {
      case holder: ScAnnotationsHolder => Some(holder)
      case _                           => None
    }

    annotatedElement match {
      case Some(holder) =>
        holder.annotations.flatMap(_.convertTo[UAnnotation](this)).asJava
      case _ => JavaCollectionsCommon.newEmptyJavaList
    }
  }
}
