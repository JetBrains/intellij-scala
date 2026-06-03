package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class UnresolvedTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "unresolved"

  def testNamedParameter(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testRef(): Unit = doTest()
  def testType(): Unit = doTest()
}
