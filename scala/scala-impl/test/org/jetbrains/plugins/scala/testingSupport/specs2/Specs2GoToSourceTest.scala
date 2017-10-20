package org.jetbrains.plugins.scala.testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 26.01.2015.
 */
abstract class Specs2GoToSourceTest extends Specs2TestCase {
  val testName = "Specs2GoToSourceTest"
  addSourceFile(testName + ".scala",
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

  def testGoToSuccessfulLocation(): Unit = {
    runGoToSourceTest(4, 8, testName + ".scala",
      checkConfigAndSettings(_, testName, "run fine"),
      List("[root]", testName, "Successful test should", "run fine"), 4)
  }

  def testGoToPendingLocation(): Unit = {
    runGoToSourceTest(9, 8, testName + ".scala",
      checkConfigAndSettings(_, testName, "be pending"),
      List("[root]", testName, "Pending test should", "be pending"), 9)
  }

  def testGoToIgnoredLocation(): Unit = {
    runGoToSourceTest(14, 8, testName + ".scala",
      checkConfigAndSettings(_, testName, "be ignored"),
      List("[root]", testName, "Ignored test should", "be ignored"), 14)
  }

  def testGoToFailedLocation(): Unit = {
    runGoToSourceTest(19, 8, testName + ".scala",
      checkConfigAndSettings(_, testName, "fail"),
      List("[root]", testName, "Failed test should", "fail"), 19)
  }
}
