package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.ScalaTestSingleTestTest

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
class Scalatest2_10_1_9_2_SelectedTestsTest extends {
  override val featureSpecConfigTestName = "Feature 1 Scenario: Scenario A"
  override val featureSpecTestPath = List("[root]", "Feature 1 Scenario: Scenario A")
  override val flatSpecTestPath = List("[root]", "A FlatSpecTest should be able to run single test")
  override val freeSpecPathTestPath = List("[root]", "A FreeSpecTest should be able to run single test")
  override val freeSpecTestPath = List("[root]", "A FreeSpecTest should be able to run single tests")
  override val funSpecTestPath = List("[root]", "FunSpecTest should launch single test")
  override val propSpecTestPath = List("[root]", "Single tests should run")
  override val funSuiteTestPath = List("[root]", "should run single test")
  override val wordSpecTestPath = List("[root]", "WordSpecTest should Run single test")
} with Scalatest2_10_1_9_2_Base with ScalaTestSingleTestTest {
}
