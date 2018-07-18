package org.jetbrains.plugins.scala
package lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Nikolay.Tropin
 * 2014-10-17
 */
// TODO Implement selectively, not by ScExpression
trait ImplicitArgumentsOwner extends ScalaPsiElement {

  /**
   * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
   * In case of implicit parameter with type ClassManifest[T]
   * this method will return ClassManifest with substitutor of type T.
   * @return implicit parameters used for this expression
   */
  def findImplicitArguments: Option[Seq[ScalaResolveResult]]
}

object ImplicitArgumentsOwner {
  def unapply(e: ImplicitArgumentsOwner): Option[Seq[ScalaResolveResult]] = e.findImplicitArguments
}
