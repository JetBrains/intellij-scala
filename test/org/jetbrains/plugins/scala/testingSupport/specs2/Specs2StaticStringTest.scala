package org.jetbrains.plugins.scala.testingSupport.specs2

/**
 * @author Roman.Shein
 * @since 18.06.2015.
 */
abstract class Specs2StaticStringTest extends Specs2TestCase {
  def testValString() = {
    val testClassName = "ValStringTest"
    val valFileName = testClassName + ".scala"
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

    assert(checkConfigAndSettings(createTestFromLocation(7, 7, valFileName), testClassName, "run"))
  }

  def testStringSum() = {
    val testClassName = "StringSumTest"
    val sumFileName = testClassName + ".scala"
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

    assert(checkConfigAndSettings(createTestFromLocation(6, 7, sumFileName), testClassName, "run fine"))
  }

  def testNonConst() = {
    val testClassName = "BadStringTest"
    val badFileName = testClassName + ".scala"
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
    """.stripMargin)

    assert(checkConfigAndSettings(createTestFromLocation(6, 7, badFileName), testClassName))
  }

  val methodsTestClassName = "SpecsMethodsTest"
  val methodsTestFileName = methodsTestClassName + ".scala"

  def addMethodsTest() = {
    addFileToProject(methodsTestFileName,
      """
        |import org.specs2.mutable.Specification
        |
        |class SpecsMethodsTest extends Specification {
        |  "MethodsTest" should {
        |    "  Test ".trim + "1" in {
        |      1 mustEqual 1
        |    }
        |
        |    "TeSt2".toLowerCase in {
        |      1 mustEqual 1
        |    }
        |
        |    "Test3Suffix".stripSuffix("Suffix") in {
        |      1 mustEqual 1
        |    }
        |
        |    "PrefixTest4".stripPrefix("Prefix") in {
        |      1 mustEqual 1
        |    }
        |
        |    "junkTest5".substring(4) in {
        |      1 mustEqual 1
        |    }
        |
        |    "junkTest6junk".substring(4, 9) in {
        |      1 mustEqual 1
        |    }
        |
        |    "replace7".replace("replace", "Test") in {
        |      1 mustEqual 1
        |    }
        |  }
        |}
      """.stripMargin.trim)
  }

  def testTrim() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(4, 7, methodsTestFileName), methodsTestClassName, "Test1"))
  }

  def testToLowerCase() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(8, 7, methodsTestFileName), methodsTestClassName, "test2"))
  }

  def testSuffix() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(12, 7, methodsTestFileName), methodsTestClassName, "Test3"))
  }

  def testPrefix() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(16, 7, methodsTestFileName), methodsTestClassName, "Test4"))
  }

  def testSubString1() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(20, 7, methodsTestFileName), methodsTestClassName, "Test5"))
  }

  def testSubString2() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(24, 7, methodsTestFileName), methodsTestClassName, "Test6"))
  }

  def testReplace() = {
    addMethodsTest()

    assert(checkConfigAndSettings(createTestFromLocation(28, 7, methodsTestFileName), methodsTestClassName, "Test7"))
  }
}
