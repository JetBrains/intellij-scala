package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.project.ProjectContext
import org.junit.Assert

abstract class DocumentationProviderTestBase
  extends ScalaLightCodeInsightFixtureTestAdapter
    with DocumentationTesting {

  protected def documentationProvider: DocumentationProvider

  protected implicit def projectContext: ProjectContext = super.getProject

  override protected def generateDoc(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    generateDoc(referredElement, elementAtCaret)
  }

  protected def generateDoc(referredElement: PsiElement, elementAtCaret: PsiElement): String =
    documentationProvider.generateDoc(referredElement, elementAtCaret)

  override final protected def createEditorAndFile(fileContent: String): (Editor, PsiFile) = {
    val file = createFile(fileContent)
    (myFixture.getEditor, file)
  }

  protected def createFile(fileContent: String): PsiFile

  /** see parameters of [[com.intellij.lang.documentation.DocumentationProvider#generateDoc]] */
  protected def extractReferredAndOriginalElements(editor: Editor, file: PsiFile): (PsiElement, PsiElement) = {
    val elementAtCaret = file.findElementAt(editor.getCaretModel.getOffset)
    val leaf = PsiTreeUtil.getDeepestFirst(elementAtCaret)
    val parents = leaf.parentsInFile.toArray
    parents.collectFirst {
      case reference: ScReference => (reference.resolve(), reference)
      case named: PsiNamedElement => (named, named)
    }.getOrElse {
      Assert.fail("No appropriate original element found at caret position").asInstanceOf[Nothing]
    }
  }
}
