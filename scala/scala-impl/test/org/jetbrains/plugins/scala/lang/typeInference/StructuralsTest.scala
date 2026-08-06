package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class StructuralsTest extends TypeInferenceTestBase {

  override def folderPath: Path = super.folderPath / "bugs5"

  def testSCL4724(): Unit = doTest {
    """
      |class SCL4724 {
      |  def foo(x: Set[{ val bar: Int }]) = 1
      |  def foo(s: String) = false
      |
      |  /*start*/foo(Set(new { val bar = 1 }) ++ Set(new { val bar = 2 }))/*end*/
      |}
      |//Int
    """.stripMargin.trim
  }
}
