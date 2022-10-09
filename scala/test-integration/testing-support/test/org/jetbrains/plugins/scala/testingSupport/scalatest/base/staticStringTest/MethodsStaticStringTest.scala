package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait MethodsStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "ScalaTestMethodsTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForPropSpec
       |
       |class $ClassName extends $PropSpecBase {
       |  property(" testName ".trim + "1") {
       |  }
       |
       |  property("TeStNaMe2".toLowerCase) {
       |  }
       |
       |  property("testName3Suffix".stripSuffix("Suffix")) {
       |  }
       |
       |  property("prefixtestName4".stripPrefix("prefix")) {
       |  }
       |
       |  property("junktestName5".substring(4)) {
       |  }
       |
       |  property("JunktestName6Junk".substring(4, 13)) {
       |  }
       |
       |  property("testReplace7".replace("Replace", "Name")) {
       |  }
       |}
       |""".stripMargin.trim)

  def testTrim(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(3, 7, FileName), ClassName, "testName1")
  }

  def testToLowerCase(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 7, FileName), ClassName, "testname2")
  }

  def testStripSuffix(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(9, 7, FileName), ClassName, "testName3")
  }

  def testStripPrefix(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(12, 7, FileName), ClassName, "testName4")
  }

  def testSubstring1(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(15, 7, FileName), ClassName, "testName5")
  }

  def testSubstring2(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(18, 7, FileName), ClassName, "testName6")
  }

  def testReplace(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(21, 7, FileName), ClassName, "testName7")
  }
}
