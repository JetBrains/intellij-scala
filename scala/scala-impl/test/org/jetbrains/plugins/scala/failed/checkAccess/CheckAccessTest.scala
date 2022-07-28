package org.jetbrains.plugins.scala.failed.checkAccess

import org.jetbrains.plugins.scala.lang.checkers.checkPrivateAccess.CheckPrivateAccessTestBase

class CheckAccessTest extends CheckPrivateAccessTestBase {
  override def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "failed/"

  def testSCL9212(): Unit = doTest()
}
