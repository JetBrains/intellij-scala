package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.isUnitTestMode

package object uast {

  private var _possibleSourceTypesCheckIsActive = false

  def possibleSourceTypesCheckIsActive: Boolean = {
    assert(isUnitTestMode, "This property should only be used in unit tests")
    _possibleSourceTypesCheckIsActive
  }

  def withPossibleSourceTypesCheck[T](body: => T): T = {
    assert(!possibleSourceTypesCheckIsActive)
    _possibleSourceTypesCheckIsActive = true
    try body
    finally _possibleSourceTypesCheckIsActive = false
  }
}
