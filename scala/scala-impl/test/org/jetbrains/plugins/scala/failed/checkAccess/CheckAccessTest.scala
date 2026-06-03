package org.jetbrains.plugins.scala.failed.checkAccess

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.checkers.checkPrivateAccess.CheckPrivateAccessTestBase

import java.nio.file.Path

class CheckAccessTest extends CheckPrivateAccessTestBase {
  override def shouldPass: Boolean = false

  override def folderPath: Path = super.folderPath / "failed"

  def testSCL9212(): Unit = doTest()
}
