package org.jetbrains.plugins.scala.lang

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language

package object typeInference {

  final case class SourceFile(
    sourceFileName: String,
    @Language("Scala")
    sourceFileContent: String
  )

  implicit class CodeInsightTestFixtureOps(private val fixture: CodeInsightTestFixture) extends AnyVal {
    def addFileToProject(sourceFile: SourceFile): PsiFile =
      fixture.addFileToProject(sourceFile.sourceFileName, sourceFile.sourceFileContent)

    def addFilesToProject(sourceFiles: Seq[SourceFile]): Seq[PsiFile] =
      sourceFiles.map(addFileToProject)

    def configureByText(sourceFile: SourceFile): PsiFile =
      fixture.configureByText(sourceFile.sourceFileName, sourceFile.sourceFileContent)
  }
}


