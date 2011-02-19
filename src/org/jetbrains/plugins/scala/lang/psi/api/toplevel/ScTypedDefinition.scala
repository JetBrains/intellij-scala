package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import types.result.TypingContextOwner

/**
 * Member definitions, classes, named patterns which have types
 */
trait ScTypedDefinition extends ScNamedElement with TypingContextOwner {

  /**
   * @return false for variable elements
   */
  def isStable = true

}