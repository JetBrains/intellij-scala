package org.jetbrains.plugins.scala.lang.typeInference

class StructuralsTest extends TypeInferenceTestBase {

  override def folderPath: String = super.folderPath + "bugs5/"

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
