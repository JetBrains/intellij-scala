package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.FunSuiteSingleTestTest

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
class Scalatest2_10_1_9_2_SingleTestTestDynamic extends {
  override val funSuiteTestPath = List("[root]", "should run single test")
} with Scalatest2_10_1_9_2_Base with FunSuiteSingleTestTest {
  override val useDynamicClassPath = true
}