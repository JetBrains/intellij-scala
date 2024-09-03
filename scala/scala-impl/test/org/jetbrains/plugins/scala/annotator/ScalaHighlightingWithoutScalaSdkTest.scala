package org.jetbrains.plugins.scala.annotator

import com.intellij.testFramework.fixtures.{CodeInsightTestFixture, LightJavaCodeInsightFixtureTestCase}

class ScalaHighlightingWithoutScalaSdkTest
  extends LightJavaCodeInsightFixtureTestCase
  with ScalaHighlightingTestLike {

  override protected def getFixture: CodeInsightTestFixture = myFixture

  def testDoNotShowErrorsForStandardLibraryPrimitiveTypes(): Unit = {
    assertNoErrors(
      """object MyObject {
        |  //test classes companion objects
        |  val boolean: Boolean = true
        |  val byte: Byte = 0
        |  val char: Char = 0
        |  val double: Double = 0
        |  val float: Float = 0
        |  val int: Int = 0
        |  val long: Long = 0
        |  val short: Short = 0
        |  val unit: Unit = ()
        |
        |  //test companion objects
        |  Boolean.toString
        |  Byte.toString
        |  Char.toString
        |  Double.toString
        |  Float.toString
        |  Int.toString
        |  Long.toString
        |  Short.toString
        |  Unit.toString
        |
        |  //test methods
        |  boolean.unary_!
        |  !boolean
        |  boolean || boolean
        |
        |  byte.toInt
        |  byte.unary_~
        |  ~byte
        |  byte + byte
        |
        |  char.toInt
        |  double.toInt
        |  float.toInt
        |  int.toInt
        |  long.toInt
        |  short.toInt
        |}
        |""".stripMargin
    )
  }

  def testShowErrorsForUnresolvedClasses(): Unit = {
    assertErrorsText(
      """object MyObject {
        |  val x: UnresolvedClass = unresolvedReference
        |}
        |""".stripMargin,
      """Error(UnresolvedClass,Cannot resolve symbol UnresolvedClass)
        |Error(unresolvedReference,Cannot resolve symbol unresolvedReference)""".stripMargin
    )
  }
}
