package org.jetbrains.plugins.scala
package lang
package psi
package uast
package baseAdapters

import java.lang

import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.uast.{UMultiResolvable, UResolvable}

import scala.jdk.CollectionConverters._

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
      .map(_.multiResolveScala(incomplete = false).toSeq: Seq[ResolveResult])
      .getOrElse(Seq.empty)
      .asJava
}
