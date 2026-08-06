package org.jetbrains.plugins.scala
package lang
package psi
package uast
package baseAdapters

import java.{util => ju}

import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.uast.converter.Scala2UastConverter._
import org.jetbrains.uast.{UAnnotated, UAnnotation}

import scala.jdk.CollectionConverters._

/**
  * Scala adapter of the [[UAnnotated]].
  * Provides:
  *  - default implementations based on `sourcePsi`
  *
  * @note Just handy util - it is not obligatory to be mixed in by according ScU*** elements.
  * @example inherited by some ScU*** elements in [[declarations]] and [[expressions]]
  */
trait ScUAnnotated extends UAnnotated {

  override def getUAnnotations: ju.List[UAnnotation] = getSourcePsi match {
    case holder: ScAnnotationsHolder =>
      holder.annotations
        .flatMap(_.convertTo[UAnnotation](parent = this))
        .asJava
    case _ => ju.Collections.emptyList()
  }
}
