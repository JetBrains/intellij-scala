package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.SpecialCharactersTest

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
class Scalatest2_10_1_9_2_SpecialCharactersTest extends {
  override val commaTestPath = List("[root]", "Comma , test should contain , comma")
  override val exclamationTestPath = List("[root]", "! test should contain !")
  override val tickTestPath = List("[root]", "tick ' test should contain '")
  override val tildeTestPath = List("[root]", "tilde ~ test should contain ~")
  override val backtickTestPath = List("[root]", "backtick ` test should contain `")
} with Scalatest2_10_1_9_2_Base with SpecialCharactersTest {
}
