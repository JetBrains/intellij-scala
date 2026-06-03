package org.jetbrains.plugins.scala

import org.jetbrains.annotations.Nls

import scala.collection.immutable.StringOps

/**
 * Marks a string as containing a natural language string
 *
 * Use when @Nls isn't possible... like in tuples
 *
 * @param nls the content
 */
case class NlsString(@Nls nls: String) extends AnyVal {
  override def toString: String = nls
}

object NlsString {
  // this makes the creation of an NlsString @Nls
  @Nls
  def apply(@Nls nls: String): NlsString = new NlsString(nls)

  /**
   * Use to force a string to become an NlsString
   */
  @Nls
  def force(nls: String): NlsString = {
    //noinspection ReferencePassedToNls
    new NlsString(nls)
  }

  import scala.language.implicitConversions

  /**
   * Implicit conversion to a normal string
   */
  @Nls
  implicit def toNormalString(nlsString: NlsString): String =
    nlsString.nls

  /**
   * Allow normal string operations
   */
  implicit def toStringOps(nlsString: NlsString): StringOps =
    new StringOps(nlsString.nls)

  /**
   * TODO:
   *   Some Configurables use the display name as id for themselves,
   *   but the search indexer cannot really handle that...
   *   So let's use this method until IJPL-166219 is resolved
   */
  @Nls
  def displayNameAndConfigurableId(nls: String): NlsString = force(nls)
}