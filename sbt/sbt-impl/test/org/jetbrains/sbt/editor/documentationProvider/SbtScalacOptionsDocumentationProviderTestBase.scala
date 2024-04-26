package org.jetbrains.sbt.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.editor.documentationProvider.base.DocumentationProviderTestBase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert

abstract class SbtScalacOptionsDocumentationProviderTestBase extends DocumentationProviderTestBase {

  override protected def documentationProvider: DocumentationProvider = new SbtScalacOptionsDocumentationProvider

  override protected def configureFixtureFromText(fileContent: String): Unit =
    myFixture.configureByText(SbtFileType, fileContent)

  override protected def extractReferredAndOriginalElements(editor: Editor, file: PsiFile): (PsiElement, PsiElement) = {
    val elementAtCaretOriginal = file.findElementAt(editor.getCaretModel.getOffset)
    val leaf = PsiTreeUtil.getDeepestFirst(elementAtCaretOriginal)
    val parents = leaf.parentsInFile.toArray

    val (referredElement, elementAtCaret) = parents.collectFirst {
      case str: ScStringLiteral => (str, leaf)
    }.getOrElse {
      Assert.fail("No appropriate original element found at caret position").asInstanceOf[Nothing]
    }

    val customDocumentationElement = documentationProvider.getCustomDocumentationElement(editor, file, elementAtCaret, 0)
    (
      Option(customDocumentationElement).getOrElse(referredElement),
      elementAtCaret
    )
  }
}
