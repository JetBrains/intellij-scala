package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestWholeSuiteTest

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
class Scalatest2_10_1_9_2_WholeSuiteTest extends {
  override val featureSpecTestPaths = List(List("[root]", "FeatureSpecTest", "Feature 1 Scenario: Scenario A"),
    List("[root]", "FeatureSpecTest", "Feature 1 Scenario: Scenario B"),
    List("[root]", "FeatureSpecTest", "Feature 2 Scenario: Scenario C"))
  override val flatSpecTestPaths = List(List("[root]", "FlatSpecTest", "A FlatSpecTest should be able to run single test"),
    List("[root]", "FlatSpecTest", "A FlatSpecTest should not run other tests"))
  override val freeSpecTestPaths = List(List("[root]", "FreeSpecTest", "A FreeSpecTest should be able to run single tests"),
    List("[root]", "FreeSpecTest", "A FreeSpecTest should not run tests that are not selected"))
  override val funSpecTestPaths = List(List("[root]", "FunSpecTest", "FunSpecTest should launch single test"),
    List("[root]", "FunSpecTest", "FunSpecTest should not launch other tests"))
  override val wordSpecTestPaths = List(List("[root]", "WordSpecTest", "WordSpecTest should Run single test"),
    List("[root]", "WordSpecTest", "WordSpecTest should ignore other tests"))
} with Scalatest2_10_1_9_2_Base with ScalaTestWholeSuiteTest {
}
