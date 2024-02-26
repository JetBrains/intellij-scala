package org.jetbrains.plugins.scala

import junit.framework.TestCase
import org.junit.Assert.fail

class SupposedToFailTest extends TestCase {

  def testSupposedToFail(): Unit = {
    fail("This test is supposed to fail to prevent merge")
  }
}
