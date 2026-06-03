package org.jetbrains.plugins.scala
package lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class InterpolatedStringTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "interpolatedString"

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
