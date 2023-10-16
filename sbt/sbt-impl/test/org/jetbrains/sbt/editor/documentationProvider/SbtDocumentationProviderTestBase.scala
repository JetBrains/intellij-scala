package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.documentationProvider.base.DocumentationProviderTestBase
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsScalaDocContentTesting
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.sbt.language.SbtFileType

abstract class SbtDocumentationProviderTestBase extends DocumentationProviderTestBase
  with ScalaDocumentationsScalaDocContentTesting {

  protected val commonDescription = """This is a description for some-key"""

  override protected def documentationProvider = new SbtDocumentationProvider

  protected def doGenerateSbtDocDescriptionTest(sbtContent: String, expectedDocShort: String): Unit =
    doGenerateDocContentTest(sbtContent, expectedDocShort)

  override protected def createFile(fileContent: String): PsiFile = {
    val fileText =
      s"""import sbt._
         |import sbt.KeyRanks._
         |$fileContent
         |""".stripMargin.withNormalizedSeparator
    myFixture.configureByText(SbtFileType, fileText)
  }
}
