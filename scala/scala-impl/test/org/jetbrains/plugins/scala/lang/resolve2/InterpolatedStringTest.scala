package org.jetbrains.plugins.scala
package lang.resolve2

class InterpolatedStringTest extends ResolveTestBase {
  override def folderPath: String = super.folderPath + "interpolatedString/"

  def testPrefixResolve(): Unit = {
    doTest()
  }

  def testResolveImplicit(): Unit = {
    doTest()
  }
  
  def testResolveInsideString(): Unit = {
    doTest()
  }
}
