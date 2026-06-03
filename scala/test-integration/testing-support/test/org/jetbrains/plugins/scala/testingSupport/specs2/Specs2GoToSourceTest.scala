package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2GoToSourceTest extends Specs2TestCase {

  val testName = "Specs2GoToSourceTest"
  val fileName = testName + ".scala"

  addSourceFile(fileName,
    s"""import org.specs2.mutable.Specification
       |
       |class $testName extends Specification {
       |  "Successful test" should {
       |    "run fine" in {
       |      success
       |    }
       |  }
       |  "Pending test" should {
       |    "be pending" in {
       |      pending
       |    }
       |  }
       |  "Ignored test" should {
       |    "be ignored" in {
       |      skipped
       |    }
       |  }
       |  "Failed test" should {
       |    "fail" in {
       |      failure
       |    }
       |  }
       |}""".stripMargin
  )

  def testGoToSuccessfulLocation(): Unit =
    runGoToSourceTest(
      loc(fileName, 4, 8),
      assertConfigAndSettings(_, testName, "run fine"),
      TestNodePath("[root]", testName, "Successful test should", "run fine"),
      sourceLine = 4
    )

  def testGoToPendingLocation(): Unit =
    runGoToSourceTest(
      loc(fileName, 9, 8),
      assertConfigAndSettings(_, testName, "be pending"),
      TestNodePath("[root]", testName, "Pending test should", "be pending"),
      sourceLine = 9
    )

  def testGoToIgnoredLocation(): Unit =
    runGoToSourceTest(
      loc(fileName, 14, 8),
      assertConfigAndSettings(_, testName, "be ignored"),
      TestNodePath("[root]", testName, "Ignored test should", "be ignored"),
      sourceLine = 14
    )

  def testGoToFailedLocation(): Unit =
    runGoToSourceTest(
      loc(fileName, 19, 8),
      assertConfigAndSettings(_, testName, "fail"),
      TestNodePath("[root]", testName, "Failed test should", "fail"),
      sourceLine = 19
    )

}
