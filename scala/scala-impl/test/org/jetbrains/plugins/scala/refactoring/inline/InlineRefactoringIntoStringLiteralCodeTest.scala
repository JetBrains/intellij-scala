package org.jetbrains.plugins.scala.refactoring.inline
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.util.assertions.PsiAssertions.assertNoParserErrors
import org.junit.Assert.assertEquals

/**
 * NOTE: prefer using [[InlineRefactoringTestBase]]-based tests.<br>
 * This test class should be used for complex scenarios which is hard to describe using simple single-test file
 */
class InlineRefactoringIntoStringLiteralCodeTest extends InlineRefactoringTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override def folderPath: String = null //not used

  def testInlineTopLevelUsageInMultipleFiles(): Unit = {
    val definitionsFile = configureScalaFromFileText(
      s"""val ${CARET}topLevelValue = "line1\\nline2"
         |
         |val topLevelValueOther = ???
         |
         |def topLevelDef = ???
         |
         |class Usage0 {
         |  s\"\"\"$$topLevelValue
         |     |
         |     |$$topLevelValue
         |     |
         |     |$$topLevelValue\"\"\".stripMargin
         |
         |  s\"\"\"$$topLevelValue
         |     #$$topLevelValue
         |     #$$topLevelValue\"\"\".stripMargin('#')
         |}""".stripMargin
    )

    val usageFile1 = addScalaFileToProject("Usage1.scala",
      s"""class Usage1 {
         |  s\"\"\"$$topLevelValue
         |     |
         |     |$$topLevelValue
         |     |
         |     |$$topLevelValue\"\"\".stripMargin
         |
         |  s\"\"\"$$topLevelValue
         |     #$$topLevelValue
         |     #$$topLevelValue\"\"\".stripMargin('#')
         |}""".stripMargin
    )

    val usageFile2 = addScalaFileToProject("Usage2.scala",
      s"""class Usage2 {
         |  s\"\"\"$$topLevelValue
         |     |
         |     |$$topLevelValue
         |     |
         |     |$$topLevelValue\"\"\".stripMargin
         |
         |  s\"\"\"$$topLevelValue
         |     #$$topLevelValue
         |     #$$topLevelValue\"\"\".stripMargin('#')
         |}""".stripMargin
    )

    assertNoParserErrors(definitionsFile)
    assertNoParserErrors(usageFile1)
    assertNoParserErrors(usageFile2)

    val elementAtCaret = getElementAtCaret
    invokeInlineHandler(elementAtCaret)

    getFixture.getDocument(definitionsFile).commit(getProject)
    getFixture.getDocument(usageFile1).commit(getProject)
    getFixture.getDocument(usageFile2).commit(getProject)

    assertFileText(definitionsFile,
      s"""val topLevelValueOther = ???
         |
         |def topLevelDef = ???
         |
         |class Usage0 {
         |  \"\"\"line1
         |    |line2
         |    |
         |    |line1
         |    |line2
         |    |
         |    |line1
         |    |line2\"\"\".stripMargin
         |
         |  \"\"\"line1
         |    #line2
         |    #line1
         |    #line2
         |    #line1
         |    #line2\"\"\".stripMargin('#')
         |}""".stripMargin
    )

    assertFileText(usageFile1,
      s"""class Usage1 {
         |  \"\"\"line1
         |    |line2
         |    |
         |    |line1
         |    |line2
         |    |
         |    |line1
         |    |line2\"\"\".stripMargin
         |
         |  \"\"\"line1
         |    #line2
         |    #line1
         |    #line2
         |    #line1
         |    #line2\"\"\".stripMargin('#')
         |}""".stripMargin
    )

    assertFileText(usageFile2,
      s"""class Usage2 {
         |  \"\"\"line1
         |    |line2
         |    |
         |    |line1
         |    |line2
         |    |
         |    |line1
         |    |line2\"\"\".stripMargin
         |
         |  \"\"\"line1
         |    #line2
         |    #line1
         |    #line2
         |    #line1
         |    #line2\"\"\".stripMargin('#')
         |}""".stripMargin
    )
  }

  private def assertFileText(file: PsiFile, expectedText: String): Unit = {
    assertEquals(
      s"File text mismatch ${file.name}",
      expectedText,
      file.getText
    )
  }
}
