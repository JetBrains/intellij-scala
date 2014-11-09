package org.jetbrains.plugins.scala
package lang.resolve2

/**
 * User: Dmitry Naydanov
 * Date: 4/2/12
 */

class InterpolatedStringTest extends ResolveTestBase {
  override def folderPath: String = super.folderPath + "interpolatedString/"

  def testPrefixResolve() {
    doTest()
  }

  def testResolveImplicit() {
    doTest()
  }
  
  def testResolveInsideString() {
    doTest()
  }
}
