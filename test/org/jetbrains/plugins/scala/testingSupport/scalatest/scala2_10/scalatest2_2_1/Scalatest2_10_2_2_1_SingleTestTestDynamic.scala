package org.jetbrains.plugins.scala
package testingSupport.scalatest.scala2_10.scalatest2_2_1

import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.FunSuiteSingleTestTest

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
class Scalatest2_10_2_2_1_SingleTestTestDynamic extends Scalatest2_10_2_2_1_Base with FunSuiteSingleTestTest {
  override val useDynamicClassPath = true
}
