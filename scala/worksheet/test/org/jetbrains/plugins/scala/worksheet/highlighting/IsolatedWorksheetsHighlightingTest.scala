package org.jetbrains.plugins.scala.worksheet.highlighting

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions._
import org.junit.Assert._

class IsolatedWorksheetsHighlightingTest extends ScalaHighlightingTestBase {

  private val WorksheetFileName = "worksheet.sc"
  private val AnotherWorksheetFileName = "another_worksheet.sc"

  private val PackagePath = "my/package/"
  private val PackageName = "my.package"

  private val code =
    s"""class MyClass
       |trait MyTrait
       |object MyObject
       |def myFunction(i: Int): String = ???
       |val myValue = 23
       |var myVariable = 23
       |
       |new MyClass
       |new MyTrait {}
       |println(MyObject)
       |myFunction(42)
       |println(myValue)
       |println(myVariable)
       |
       |new ExternalClass
       |new ExternalTrait {}
       |println(ExternalObject)
       |ExternalObject.f
       |externalFunction(42)
       |println(externalValue)
       |println(externalVariable)
       |""".stripMargin

  private val codeFromAnotherWorksheet =
    s"""class ExternalClass
       |trait ExternalTrait
       |object ExternalObject { def f: String = ??? }
       |def externalFunction(i: Int): String = ???
       |val externalValue = 42
       |var externalVariable = 42scala
       |""".stripMargin

  private val expectedMessages = List(
    "Cannot resolve symbol ExternalClass",
    "Cannot resolve symbol ExternalTrait",
    "Cannot resolve symbol ExternalObject",
    "Cannot resolve symbol externalFunction",
    "Cannot resolve symbol externalValue",
    "Cannot resolve symbol externalVariable",
  )

  def testDeclarationsInOtherWorksheetShouldNotBeAvailableInCurrentWorksheet(): Unit = {
    myFixture.addFileToProject(AnotherWorksheetFileName, codeFromAnotherWorksheet)
    val actualMessages = errorsFromScalaCode(code, WorksheetFileName).map(_.message).distinct
    assertCollectionEquals(expectedMessages, actualMessages)
    assertAnotherWorksheetIsAtTheSameLevel()
  }

  def testDeclarationsInOtherWorksheetShouldNotBeAvailableInCurrentWorksheet_NotDefaultPackage(): Unit = {
    myFixture.addFileToProject(PackagePath + AnotherWorksheetFileName, codeFromAnotherWorksheet)
    val file = myFixture.addFileToProject(PackagePath + WorksheetFileName, code)
    val actualMessages = errorsFromScalaCode(file).map(_.message).distinct
    assertCollectionEquals(expectedMessages, actualMessages)
    assertAnotherWorksheetIsAtTheSameLevel(file)
  }

  def testDeclarationsInWorksheetShouldNotBeAvailableInNormalScalaCode(): Unit = {
    myFixture.addFileToProject(AnotherWorksheetFileName, codeFromAnotherWorksheet)
    val codeWrapped =
      s"""class Wrapper {
         |$code
         |}""".stripMargin
    val actualMessages = errorsFromScalaCode(codeWrapped, "Wrapper.scala").map(_.message).distinct
    assertCollectionEquals(expectedMessages, actualMessages)
    assertAnotherWorksheetIsAtTheSameLevel()
  }

  def testDeclarationsInWorksheetShouldNotBeAvailableInNormalScalaCode_NotDefaultPackage(): Unit = {
    myFixture.addFileToProject(PackagePath + AnotherWorksheetFileName, codeFromAnotherWorksheet)
    val codeWrapped =
      s"""package $PackageName
         |class Wrapper {
         |$code
         |}""".stripMargin
    val file = myFixture.addFileToProject(PackagePath + "Wrapper.scala", codeWrapped)
    val actualMessages = errorsFromScalaCode(file).map(_.message).distinct
    assertCollectionEquals(expectedMessages, actualMessages)
    assertAnotherWorksheetIsAtTheSameLevel(file)
  }

  def testDeclarationsInOtherWorksheetShouldNotAffectCurrentWorksheetTypeInference(): Unit = {
    val code = """val nums = 1 :: 2 :: 3 :: Nil"""
    myFixture.addFileToProject(AnotherWorksheetFileName, """object Nil""".stripMargin)
    assertNothing(errorsFromScalaCode(code, WorksheetFileName))
    assertAnotherWorksheetIsAtTheSameLevel()
  }

  def testDeclarationsInOtherWorksheetShouldNotAffectCurrentWorksheetTypeInference_NotDefaultPackage(): Unit = {
    val code = """val nums = 1 :: 2 :: 3 :: Nil"""
    myFixture.addFileToProject(PackagePath + AnotherWorksheetFileName, """object Nil""".stripMargin)
    val file = myFixture.addFileToProject(PackagePath + WorksheetFileName, code)
    assertNothing(errorsFromScalaCode(file))
    assertAnotherWorksheetIsAtTheSameLevel(file)
  }

  private def assertAnotherWorksheetIsAtTheSameLevel(): Unit =
    assertAnotherWorksheetIsAtTheSameLevel(myFixture.getFile)

  private def assertAnotherWorksheetIsAtTheSameLevel(file: PsiFile): Unit = {
    val siblingFiles = file.getContainingDirectory.getFiles.map(_.getVirtualFile)
    siblingFiles.find(_.getName.endsWith(AnotherWorksheetFileName)) match {
      case Some(_) =>
      case None    => fail(s"another worksheet was intended to be located near current worksheet, but found:\n:${siblingFiles.mkString("\n")}")
    }
  }
}
