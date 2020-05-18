package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.documentationProvider.DocumentationProviderTestBase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert._

// TODO: it takes too long to setup project, we should reuse project in each test case
abstract class SbtDocumentationProviderTestBase extends DocumentationProviderTestBase {

  protected val description = """This is a description for some-key"""

  private val WrapperHtmlReg = """^\s*<html><body><pre>.+?</pre>(.+)</body></html>\s*$""".r

  override protected def documentationProvider = new SbtDocumentationProvider

  override protected def doShortGenerateDocTest(sbtContent: String, expectedDocShort: String): Unit = {
    val actualDoc = generateDoc(sbtContent)
    val actualDocShort = actualDoc match {
      case WrapperHtmlReg(inner) => inner
      case null                  => fail("No documentation is returned").asInstanceOf[Nothing]
      case _                     => fail(s"Couldn't extract short documentation from text:\n$actualDoc ").asInstanceOf[Nothing]
    }
    assertDocHtml(s"<br/><b>$expectedDocShort</b>", actualDocShort)
  }

  override protected def createFile(fileContent: String): PsiFile = {
    val fileText =
      s"""import sbt._
         |import sbt.KeyRanks._
         |$fileContent
         |""".stripMargin.withNormalizedSeparator
    getFixture.configureByText(SbtFileType, fileText)
  }
}
