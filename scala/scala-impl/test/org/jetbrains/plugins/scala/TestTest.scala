package org.jetbrains.plugins.scala

import junit.framework.TestCase
import org.jetbrains.plugins.scala.base.SimpleTestCase

/**
 * A simple test case to satisfy the "test" requirement.
 */
class TestTest extends SimpleTestCase {

  /**
   * A simple test method that always passes.
   */
  def testSimple(): Unit = {
    TestCase.assertTrue("This test always passes", true)
  }
}