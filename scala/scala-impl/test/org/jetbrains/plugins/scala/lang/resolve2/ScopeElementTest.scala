package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ScopeElementTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "scope" / "element"

  def testBlock(): Unit = doTest()
  def testCaseClass(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
}
