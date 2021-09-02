package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.editor.documentationProvider.DocumentationProviderTestBase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.sbt.MockSbtBase
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert

abstract class SbtScalacOptionsDocumentationProviderTestBase extends DocumentationProviderTestBase {
  self: MockSbtBase =>

  override protected def documentationProvider: DocumentationProvider = new SbtScalacOptionsDocumentationProvider

  override protected def createFile(fileContent: String): PsiFile =
    getFixture.configureByText(SbtFileType, fileContent)

  override protected def generateDoc(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    val customDocumentationElement = documentationProvider.getCustomDocumentationElement(editor, file, elementAtCaret, 0)
    generateDoc(Option(customDocumentationElement).getOrElse(referredElement), elementAtCaret)
  }

  override protected def extractReferredAndOriginalElements(editor: Editor, file: PsiFile): (PsiElement, PsiElement) = {
    val elementAtCaret = file.findElementAt(editor.getCaretModel.getOffset)
    val leaf = PsiTreeUtil.getDeepestFirst(elementAtCaret)
    val parents = leaf.parentsInFile.toArray
    parents.collectFirst {
      case str: ScStringLiteral => (str, leaf)
    }.getOrElse {
      Assert.fail("No appropriate original element found at caret position").asInstanceOf[Nothing]
    }
  }
}
