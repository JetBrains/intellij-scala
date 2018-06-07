package org.jetbrains.plugins.scala
package lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Nikolay.Tropin
 * 2014-10-17
 */
// TODO ImplicitArgumentsOwner
// TODO Implement selectiverly, not by ScExpression
trait ImplicitParametersOwner extends ScalaPsiElement {

  /**
   * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
   * In case of implicit parameter with type ClassManifest[T]
   * this method will return ClassManifest with substitutor of type T.
   * @return implicit parameters used for this expression
   */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]]
}

object ImplicitParametersOwner {
  def unapply(e: ImplicitParametersOwner): Option[Seq[ScalaResolveResult]] = e.findImplicitParameters
}
