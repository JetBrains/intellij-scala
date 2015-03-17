package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 17.01.2015.
 */
trait ScalaTestDuplicateConfigTest extends IntegrationTest {
  def testDuplicateConfig() {
    addFileToProject("DuplicateConfigTest.scala",
      """
        |import org.scalatest._
        |
        |class DuplicateConfigTest extends FlatSpec {
        | "A DuplicateConfigTest" should "create only one run configuration" in {
        | }
        | "Dummy test" should "do nothing, here just for fun"{}
        |}
      """.stripMargin.trim()
    )

    runDuplicateConfigTest(4, 10, "DuplicateConfigTest.scala",
      checkConfigAndSettings(_, "DuplicateConfigTest", "A DuplicateConfigTest should create only one run configuration")
    )
  }
}
