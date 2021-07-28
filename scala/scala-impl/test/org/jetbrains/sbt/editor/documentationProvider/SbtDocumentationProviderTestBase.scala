package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.documentationProvider.{DocumentationProviderTestBase, ScalaDocumentationsSectionsTesting}
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.sbt.MockSbtBase
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert._

// TODO: it takes too long to setup project, we should reuse project in each test case
abstract class SbtDocumentationProviderTestBase extends DocumentationProviderTestBase
  with ScalaDocumentationsSectionsTesting {
  self: MockSbtBase =>

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
    getFixture.configureByText(SbtFileType, fileText)
  }
}
