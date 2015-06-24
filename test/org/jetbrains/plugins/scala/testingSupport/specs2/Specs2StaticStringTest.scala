package org.jetbrains.plugins.scala.testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 18.06.2015.
 */
abstract class Specs2StaticStringTest extends Specs2TestCase {
  def testValString() = {
    val valFileName = "ValStringTest.scala"
    addFileToProject(valFileName,
    """
      |import org.specs2.mutable.Specification
      |
      |class ValStringTest extends Specification {
      |  val testName = "run"
      |
      |  "ValStringTest" should {
      |    testName in {
      |      1 mustEqual 1
      |    }
      |  }
      |}
      |    """.stripMargin)

    assert(checkConfigAndSettings(createTestFromLocation(7, 7, valFileName), "ValStringTest", "run"))
  }

  def testStringSum() = {
    val sumFileName = "StringSumTest.scala"
    addFileToProject(sumFileName,
    """
      |import org.specs2.mutable.Specification
      |
      |class StringSumTest extends Specification {
      |
      |  "ValStringTest" should {
      |    "run" + " fine" in {
      |      1 mustEqual 1
      |    }
      |  }
      |}
    """.stripMargin)

    assert(checkConfigAndSettings(createTestFromLocation(6, 7, sumFileName), "StringSumTest", "run fine"))
  }

  def testNonConst() = {
    val testClassName = "BadStringTest"
    val badFileName = "BadStringTest.scala"
    addFileToProject(badFileName,
    """
      |import org.specs2.mutable.Specification
      |
      |class BadStringTest extends Specification {
      |  var varTest = "var"
      |  "ValStringTest" should {
      |    varTest in {
      |      1 mustEqual 1
      |    }
      |
      |    "run" + System.currentTimeMillis() in {
      |      1 mustEqual 1
      |    }
      |  }
      |}
      |    """.stripMargin)

    assert(checkConfigAndSettings(createTestFromLocation(6, 7, badFileName), testClassName))
  }
}
