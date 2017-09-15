package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.scalatest.IgnoredSpecTest
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 22.01.2015.
 */
@Category(Array(classOf[SlowTests]))
class Scalatest2_10_1_9_2_IngoredTestTest extends {
  override val ignoredTestPath =
    List("[root]", "IgnoredTestSpec", "An IgnoredTestSpec should be ignored and have proper suffix !!! IGNORED !!!")
  override val succeededTestPath =
    List("[root]", "IgnoredTestSpec", "An IgnoredTestSpec should run tests")
} with Scalatest2_10_1_9_2_Base with IgnoredSpecTest
