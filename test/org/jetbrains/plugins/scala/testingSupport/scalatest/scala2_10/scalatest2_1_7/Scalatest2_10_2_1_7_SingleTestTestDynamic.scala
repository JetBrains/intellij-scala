package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest2_1_7

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.FunSuiteSingleTestTest
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
@Category(Array(classOf[SlowTests]))
class Scalatest2_10_2_1_7_SingleTestTestDynamic extends Scalatest2_10_2_1_7_Base with FunSuiteSingleTestTest {
  override val useDynamicClassPath = true
}
