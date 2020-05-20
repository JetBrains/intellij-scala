package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.junit.Assert

abstract class DocumentationProviderTestBase
  extends ScalaLightCodeInsightFixtureTestAdapter
    with DocumentationTestLike {

  protected def documentationProvider: DocumentationProvider

  override protected def generateDoc(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    documentationProvider.generateDoc(referredElement, elementAtCaret)
  }

  override final protected def createEditorAndFile(fileContent: String): (Editor, PsiFile) = {
    val file = createFile(fileContent)
    (myFixture.getEditor, file)
  }

  protected def createFile(fileContent: String): PsiFile

  /** see parameters of [[com.intellij.lang.documentation.DocumentationProvider#generateDoc]] */
  protected def extractReferredAndOriginalElements(editor: Editor, file: PsiFile): (PsiElement, PsiElement) = {
    val elementAtCaret = file.findElementAt(editor.getCaretModel.getOffset)
    val namedElement = elementAtCaret.parentOfType(classOf[PsiNamedElement])
    namedElement match {
      case Some(definition) => // if caret is placed at a the key definition itself
        (definition, definition)
      case None =>
        elementAtCaret.parentOfType(classOf[ScReferenceExpression]) match {
          case Some(reference) => // if caret is placed at a reference to the key definition
            val resolved = reference.resolve()
            (resolved, reference)
          case None =>
            Assert.fail("No appropriate original element found at caret position").asInstanceOf[Nothing]
        }
    }
  }
}
