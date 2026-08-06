package org.jetbrains.plugins.scala.editor

import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDefinitionWithAssignment

object ScalaEditorUtils {

  /**
   * If caret is in the end of the document, file.findElementAt returns null.<br>
   * In this case, this method returns leaf last element in the file if it's non empty.
   */
  @Nullable
  def findElementAtCaret_WithFixedEOF(file: PsiFile, document: Document, caretOffset: Int): PsiElement =
    findElementAtCaret_WithFixedEOF(file, document.getTextLength, caretOffset)

  @Nullable
  def findElementAtCaret_WithFixedEOF(file: PsiFile, documentLength: => Int, caretOffset: Int): PsiElement = {
    val elementAtCaret = file.findElementAt(caretOffset)
    if (elementAtCaret == null && documentLength == caretOffset)
      deepestLastChild(file)
    else
      elementAtCaret
  }

  def deepestLastChild(file: PsiFile): PsiElement = {
    val deepest = PsiTreeUtil.getDeepestLast(file)
    if (deepest eq file) // if file is empty, getDeepestLast returns the file itself
      null
    else
      deepest
  }

  /**
   * If the caret is right after some element but in the beginning of a whitespace this method returns the element
   * Example1: {{{
   *   new MyClass<caret> //returns 'MyClass'
   * }}}
   * Example2: {{{
   *   new MyClass   <caret> //returns whitespace '   '
   * }}}
   */
  def findElementAtCaret_WithFixedEOFAndWhiteSpace(file: PsiFile, document: Document, caretOffset: Int): PsiElement = {
    val elementAtCaret = file.findElementAt(caretOffset)
    elementAtCaret match {
      case ws: PsiWhiteSpace if caretOffset == ws.getNode.getStartOffset =>
        //in case when caret is right after the error end offset
        PsiTreeUtil.prevLeaf(ws)
      case null if document.getTextLength == caretOffset =>
        deepestLastChild(file)
      case e =>
        e
    }
  }

  /**
   * @return "editor caret offset" if caret is not located in the end of file<br>
   *         "editor caret offset - 1" otherwise
   * @note We need this method because sometimes it's not possible to use ScalaEditorUtils.find* methods.
   *       For example when we pass caret offset to the platform and it calls `file.findElementAt` itself and we don't have control on that.
   *       In this case best we can do is pass the patched offset
   */
  def caretOffsetWithFixedEof(editor: Editor): Int = {
    val caretOffset = editor.getCaretModel.getOffset
    if (caretOffset == editor.getDocument.getTextLength)
      caretOffset - 1 //if caret is in the end of
    else
      caretOffset
  }

  /**
   * @return true for any of these {{{
   *    def foo = CARET //for def/val/var
   *    extension (x: String) CARET
   *    class A: CARET`
   * }}}
   */
  def isIncompleteDefinitionError(e: PsiErrorElement): Boolean = {
    val description = e.getErrorDescription
    val isIncompleteTemplateDefinition = description == ScalaBundle.message("indented.definitions.expected")
    val isIncompleteExtension = description == ScalaBundle.message("expected.at.least.one.extension.method")
    val isIncompleteDefinitionWithAssign = e.getParent.is[ScDefinitionWithAssignment] && (
      // Note, for some reason the error is different in some cases, see SCL-23798
      description == ScalaBundle.message("expression.expected") || //example: def foo = //implement me
        description == ScalaBundle.message("wrong.expression") || //example: def foo: String = //implement me
        description == ScalaBundle.message("wrong.type") //example: type X = //implement me
      )
    Option(e.getPrevSibling).exists(_.elementType == ScalaTokenTypes.tASSIGN)
    isIncompleteTemplateDefinition ||
      isIncompleteExtension ||
      isIncompleteDefinitionWithAssign
  }
}
