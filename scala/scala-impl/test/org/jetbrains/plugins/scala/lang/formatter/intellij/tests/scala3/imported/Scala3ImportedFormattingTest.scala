package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3.imported

import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFileFactory}
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.Scala3ImportedParserTest
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

private[imported] abstract class Scala3ImportedFormattingTestBase(dir: String) extends ScalaFileSetTestCase(dir) {
  override protected def getLanguage: Language = Scala3Language.INSTANCE

  class ExpectedResult(val expectedText: String) {
    var used = false
  }

  private var currentExpectedResult = new ExpectedResult("")
  currentExpectedResult.used = true

  protected override def transform(testName: String, fileText: String, project: Project): String = {
    val file =
      PsiFileFactory.getInstance(project)
        .createFileFromText("dummy.scala", getLanguage, fileText, true, false)

    assert(currentExpectedResult.used)
    currentExpectedResult = new ExpectedResult(myPsiToString(file))

    val manager = file.getManager
    val docManager = PsiDocumentManager.getInstance(project)
    val document = docManager.getDocument(file).ensuring(_ != null, "Don't expect the document to be null")
    WriteCommandAction.runWriteCommandAction(file.getProject,
      new Runnable {
        override def run(): Unit = {
          docManager.commitDocument(document)
          CodeStyleManager.getInstance(manager).reformatText(file, 0, file.getTextLength)
          //docManager.commitDocument(document)
          //CodeStyleManager.getInstance(manager).reformat(file)
          docManager.commitDocument(document)
        }
      }
    )

    myPsiToString(file)
  }

  protected override def transformExpectedResult(text: String): String = {
    assert(!currentExpectedResult.used)
    currentExpectedResult.used = true
    currentExpectedResult.expectedText
  }

  private def myPsiToString(file: PsiElement): String = {
    val buffer = new StringBuffer
    def write(element: PsiElement, indent: Int): Unit = {
      if (element.isWhitespaceOrComment) return
      buffer.append("  " * indent)
      buffer.append(element.getNode.getElementType.toString)
      element match {
        case named: ScNamedElement => buffer.append(s": ${named.name}")
        case _ =>
      }
      if (element.firstChild.isEmpty) {
        buffer.append(s" (${element.getText})")
      }
      buffer.append("\n")
      element.children.foreach(write(_, indent + 1))
    }
    write(file, 0)
    buffer.toString.trim
  }
}

class Scala3ImportedFormattingTest extends TestCase

object Scala3ImportedFormattingTest {
  val directory: String = Scala3ImportedParserTest.directory

  def suite(): Test = new Scala3ImportedFormattingTestBase(directory) {}
}
