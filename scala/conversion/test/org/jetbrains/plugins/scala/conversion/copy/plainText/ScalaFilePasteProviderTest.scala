package org.jetbrains.plugins.scala.conversion.copy.plainText

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.{assertEquals, fail}

class ScalaFilePasteProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  private def assertSuggestedFileName(pastedCode: String, expectedFileName: String): Unit = {
    val provider = new ScalaFilePasteProvider()
    val nameWithExtension = provider.suggestedScalaFileNameForText(pastedCode, getModule).getOrElse {
      fail("Can't create scala file for pasted code").asInstanceOf[Nothing]
    }
    assertEquals("Suggested file name", expectedFileName, nameWithExtension.fullName)
  }

  def testSuggestedFileNameForClass(): Unit = {
    assertSuggestedFileName("class MyClass", "MyClass.scala")
  }

  def testSuggestedFileNameForTrait(): Unit = {
    assertSuggestedFileName("trait MyTrait", "MyTrait.scala")
  }

  def testSuggestedFileNameForObject(): Unit = {
    assertSuggestedFileName("object MyObject", "MyObject.scala")
  }

  def testSuggestedFileNameForType(): Unit = {
    assertSuggestedFileName("type MyTypeAlias = AliasedClass", "MyTypeAlias.scala")
  }

  def testSuggestedFileNameForDef(): Unit = {
    assertSuggestedFileName("def myFunction: Int = ???", "myFunction.scala")
  }

  def testSuggestedFileNameForVal(): Unit = {
    assertSuggestedFileName("val myValue: Int = ???", "myValue.scala")
  }

  def testSuggestedFileNameForVar(): Unit = {
    assertSuggestedFileName("var myVariable: Int = ???", "myVariable.scala")
  }

  def testSuggestedFileNameForValMultipleBindings(): Unit = {
    assertSuggestedFileName("val (myValueFromPattern1, myValueFromPattern2) = (???, ???)", "myValueFromPattern1.scala")
  }

  def testSuggestedFileNameForVarMultipleBindings(): Unit = {
    assertSuggestedFileName("var (myVariableFromPattern1, myVariableFromPattern2) = (???, ???)", "myVariableFromPattern1.scala")
  }

  def testSuggestedFileNameForEnum(): Unit = {
    assertSuggestedFileName(
      """enum MyEnum:
        |  case MyCase1, MyCase2""".stripMargin,
      "MyEnum.scala"
    )
  }

  def testSuggestedFileNameForGiven(): Unit = {
    assertSuggestedFileName("given myGiven: String = ???", "myGiven.scala")
  }

  def testSuggestedFileNameForExtensions(): Unit = {
    assertSuggestedFileName(
      """extension (s: String)
        |  def myExtension1: String = ???
        |  def myExtension2: String = ???""".stripMargin,
      "myExtension1.scala"
    )
  }

  def testSuggestedFileNameForExpression(): Unit = {
    assertSuggestedFileName("println(42)", "worksheet.sc")
  }
}