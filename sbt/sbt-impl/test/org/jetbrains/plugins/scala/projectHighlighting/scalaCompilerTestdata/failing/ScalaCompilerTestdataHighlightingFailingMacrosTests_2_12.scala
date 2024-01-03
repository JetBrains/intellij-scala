package org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata.failing

class ScalaCompilerTestdataHighlightingFailingMacrosTests_2_12 extends ScalaCompilerTestdataHighlightingFailingTestBase_2_12 {
  override def getTestDirName = "macros"

  def test_t8781(): Unit = doTest()
}
