package org.jetbrains.plugins.scala.testingSupport.scalatest.base

trait ScalaTestDuplicateConfigTest extends ScalaTestTestCase {

  addSourceFile("DuplicateConfigTest.scala",
    s"""$ImportsForFlatSpec
       |
       |class DuplicateConfigTest extends $FlatSpecBase {
       | "A DuplicateConfigTest" should "create only one run configuration" in {
       | }
       | "Dummy test" should "do nothing, here just for fun" in {}
       |}
       |""".stripMargin
  )

  def testDuplicateConfig(): Unit = {
    runDuplicateConfigTest(3, 10, "DuplicateConfigTest.scala",
      assertConfigAndSettings(_, "DuplicateConfigTest", "A DuplicateConfigTest should create only one run configuration")
    )
  }
}
