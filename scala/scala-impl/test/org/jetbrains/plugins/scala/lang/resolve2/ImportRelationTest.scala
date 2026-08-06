package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ImportRelationTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "import" / "relation"

  def testAbsolute(): Unit = doTest()
  def testClash(): Unit = doTest()
  def testRelative(): Unit = doTest()
  def testRoot(): Unit = doTest()
}
