package org.jetbrains.plugins.scala.lang.psi.api


/**
 * A syntax element that might represent an invocation.
 *
 * See the documentation on [[InvocationDetails]] for details.
 */
trait InvocationDetailsOwner extends ScalaPsiElement {

  /** The details of the invocation this expression syntax element is, [[None]] if it is not an invocation. */
  final def invocationDetails: Option[InvocationDetails] = InvocationDetails.of(this)
}
