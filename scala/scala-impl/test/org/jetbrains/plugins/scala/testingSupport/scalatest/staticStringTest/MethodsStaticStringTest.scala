package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait MethodsStaticStringTest extends ScalaTestTestCase {

  val methodsClassName = "ScalaTestMethodsTest"
  val methodsFileName = methodsClassName + ".scala"

  addSourceFile(methodsFileName,
    s"""
      |import org.scalatest._
      |
      |class $methodsClassName extends PropSpec {
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
      |
    """.stripMargin.trim)

  def testTrim(): Unit = {
    assertConfigAndSettings(createTestFromLocation(3, 7, methodsFileName), methodsClassName, "testName1")
  }

  def testToLowerCase(): Unit = {
    assertConfigAndSettings(createTestFromLocation(6, 7, methodsFileName), methodsClassName, "testname2")
  }

  def testStripSuffix(): Unit = {
    assertConfigAndSettings(createTestFromLocation(9, 7, methodsFileName), methodsClassName, "testName3")
  }

  def testStripPrefix(): Unit = {
    assertConfigAndSettings(createTestFromLocation(12, 7, methodsFileName), methodsClassName, "testName4")
  }

  def testSubstring1(): Unit = {
    assertConfigAndSettings(createTestFromLocation(15, 7, methodsFileName), methodsClassName, "testName5")
  }

  def testSubstring2(): Unit = {
    assertConfigAndSettings(createTestFromLocation(18, 7, methodsFileName), methodsClassName, "testName6")
  }

  def testReplace(): Unit = {
    assertConfigAndSettings(createTestFromLocation(21, 7, methodsFileName), methodsClassName, "testName7")
  }
}
