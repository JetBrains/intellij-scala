package org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters

import java.lang

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.uast.{declarations, expressions}
import org.jetbrains.uast.{UMultiResolvable, UResolvable}

import scala.collection.JavaConverters._

/**
  * Scala adapter of the [[UMultiResolvable]] with [[UResolvable]].
  * Provides:
  *  - default implementations based on `scReference`
  *
  * @note Just handy util - it is not obligatory to be mixed in by according ScU*** elements.
  * @example inherited by some ScU*** elements in [[declarations]] and [[expressions]]
  */
trait ScUMultiResolvable extends UResolvable with UMultiResolvable {
  protected def scReference: Option[ScReference]

  override def multiResolve: lang.Iterable[ResolveResult] =
    scReference
      .map(_.multiResolveScala(false).toSeq: Seq[ResolveResult])
      .getOrElse(Seq.empty)
      .asJava
}
