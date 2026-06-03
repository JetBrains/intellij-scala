package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class Scala29Test extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "scala29"

  def testSCL2913(): Unit = doTest()
}
