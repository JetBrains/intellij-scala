package org.jetbrains.plugins.scala.testingSupport.specs2

abstract class Specs2StaticStringTest extends Specs2TestCase {
  val valClassName = "ValStringTest"
  val valFileName = valClassName + ".scala"
  addSourceFile(valFileName,
    s"""
      |import org.specs2.mutable.Specification
      |
      |class $valClassName extends Specification {
      |  val testName = "run"
      |
      |  "ValStringTest" should {
      |    testName in {
      |      1 mustEqual 1
      |    }
      |  }
      |}
      |    """.stripMargin)

  def testValString(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(7, 7, valFileName), valClassName, "run")
  }

  val sumClassName = "StringSumTest"
  val sumFileName = sumClassName + ".scala"
  addSourceFile(sumFileName,
    s"""
      |import org.specs2.mutable.Specification
      |
      |class $sumClassName extends Specification {
      |
      |  "ValStringTest" should {
      |    "run" + " fine" in {
      |      1 mustEqual 1
      |    }
      |  }
      |}
    """.stripMargin)
  def testStringSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 7, sumFileName), sumClassName, "run fine")
  }

  val badClassName = "BadStringTest"
  val badFileName = badClassName + ".scala"
  addSourceFile(badFileName,
    s"""
      |import org.specs2.mutable.Specification
      |
      |class $badClassName extends Specification {
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

  def testNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 7, badFileName), badClassName)
  }

  val methodsTestClassName = "SpecsMethodsTest"
  val methodsTestFileName = methodsTestClassName + ".scala"

  addSourceFile(methodsTestFileName,
    s"""
      |import org.specs2.mutable.Specification
      |
      |class $methodsTestClassName extends Specification {
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
  def testTrim(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(4, 7, methodsTestFileName), methodsTestClassName, "Test1")
  }

  def testToLowerCase(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 7, methodsTestFileName), methodsTestClassName, "test2")
  }

  def testSuffix(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(12, 7, methodsTestFileName), methodsTestClassName, "Test3")
  }

  def testPrefix(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(16, 7, methodsTestFileName), methodsTestClassName, "Test4")
  }

  def testSubString1(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(20, 7, methodsTestFileName), methodsTestClassName, "Test5")
  }

  def testSubString2(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(24, 7, methodsTestFileName), methodsTestClassName, "Test6")
  }

  def testReplace(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(28, 7, methodsTestFileName), methodsTestClassName, "Test7")
  }
}
