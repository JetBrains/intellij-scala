package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class InheritanceAccessTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "inheritance" / "access"

  def testClashPrivateFunction(): Unit = doTest()
  def testClashProtectedFunction(): Unit = doTest()
  def testPrivateClass(): Unit = doTest()
  def testPrivateFunction(): Unit = doTest()
  def testProtectedFunction(): Unit = doTest()
}
