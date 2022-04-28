package org.jetbrains.plugins.scala.testingSupport.scalatest

trait ScalaTestDuplicateConfigTest extends ScalaTestTestCase {
  
  addSourceFile("DuplicateConfigTest.scala",
    """
      |import org.scalatest._
      |
      |class DuplicateConfigTest extends FlatSpec {
      | "A DuplicateConfigTest" should "create only one run configuration" in {
      | }
      | "Dummy test" should "do nothing, here just for fun" in {}
      |}
    """.stripMargin.trim()
  )

  def testDuplicateConfig(): Unit = {
    runDuplicateConfigTest(4, 10, "DuplicateConfigTest.scala",
      assertConfigAndSettings(_, "DuplicateConfigTest", "A DuplicateConfigTest should create only one run configuration")
    )
  }
}
