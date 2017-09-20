package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_11_2_4_15

import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 11.01.2015.
 */
@Category(Array(classOf[SlowTests]))
class Specs2_2_11_2_4_15_SingleTestTestDynamic extends Specs2_2_11_2_4_15_SingleTestTest {
  override val useDynamicClassPath = true
}
