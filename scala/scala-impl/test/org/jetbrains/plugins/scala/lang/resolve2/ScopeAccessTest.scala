package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ScopeAccessTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "scope" / "access"

  def testPrivateCompanionClass(): Unit = doTest()
  def testPrivateCompanionObject(): Unit = doTest()
  def testPrivateThisCompanionClass(): Unit = doTest()
  def testPrivateThisCompanionObject(): Unit = doTest()
}
