package org.jetbrains.plugins.scala
package lang.resolve2

import org.jetbrains.plugins.scala.util.TestUtils

/**
 * User: Dmitry Naydanov
 * Date: 4/2/12
 */

class InterpolatedStringTest extends ResolveTestBase {
  override def folderPath: String = super.folderPath + "interpolatedString/"

  override def setUp() {
    super.setUp(TestUtils.ScalaSdkVersion._2_10)
  }

  def testPrefixResolve() {
    doTest()
  }

  /*def testIdCannotResolve() { //TODO we'll do something when it will be implemented in the compiler
    doTest()
  }*/

  def testResolveInsideString() {
    doTest()
  }
}
