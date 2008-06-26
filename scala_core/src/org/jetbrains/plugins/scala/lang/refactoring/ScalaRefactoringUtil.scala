package org.jetbrains.plugins.scala.lang.refactoring

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScParenthesisedExpr
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import java.util.Comparator
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.util.ReflectionCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTryBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.lexer._

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object ScalaRefactoringUtil {
  def trimSpacesAndComments(editor: Editor, file: PsiFile) {
    var start = editor.getSelectionModel.getSelectionStart
    var end = editor.getSelectionModel.getSelectionEnd
    while (file.findElementAt(start).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(start).isInstanceOf[PsiComment]) start = start + 1
    while (file.findElementAt(end - 1).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(end - 1).isInstanceOf[PsiComment]) end = end - 1
    editor.getSelectionModel.setSelection(start, end)
  }

  def getExpression(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int): Option[ScExpression] = {
    val element1 = file.findElementAt(startOffset)
    val element2 = file.findElementAt(endOffset - 1)
    if (element1 == null || element2 == null) {
      return None
    }
    val common = PsiTreeUtil.findCommonParent(element1, element2)
    val element: ScExpression = if (common.isInstanceOf[ScExpression])
      common.asInstanceOf[ScExpression] else PsiTreeUtil.getParentOfType(common, classOf[ScExpression]);
    if (element == null || element.getTextRange.getStartOffset != startOffset) {
      return None
    }
    return Some(element)
  }

  def getEnclosingContainer(expr: ScExpression): PsiElement = {
    var parent: PsiElement = expr
    while (parent != null &&
            !parent.isInstanceOf[ScBlockExpr] &&
            !parent.isInstanceOf[ScTryBlock] &&
            !parent.isInstanceOf[ScTemplateBody] &&
            !parent.isInstanceOf[ScBlock]) parent = parent.getParent
    return parent
  }
  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile()
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(Array(virtualFile))
    return !operationStatus.hasReadonlyFiles()
  }
  def getOccurrences(expr: ScExpression, enclosingContainer: PsiElement): Array[ScExpression] = {
    val occurrences: ArrayBuffer[ScExpression] = new ArrayBuffer[ScExpression]()
    if (enclosingContainer == expr) occurrences += enclosingContainer.asInstanceOf[ScExpression]
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, expr, comparator, false)) {
          occurrences += child.asInstanceOf[ScExpression]
        } else {
          occurrences ++= getOccurrences(expr, child)
        }
      }
    return occurrences.toArray
  }
  def unparExpr(expr: ScExpression): ScExpression = {
    expr match {
      case x: ScParenthesisedExpr => {
        x.expr
      }
      case _ => expr
    }
  }
  private val comparator = new Comparator[PsiElement]() {
    def compare(element1: PsiElement, element2: PsiElement): Int = {
      if (element1 == element2) return 0
      if (element1.isInstanceOf[ScParameter] && element2.isInstanceOf[ScParameter]) {
        val name1 = element1.asInstanceOf[ScParameter].getName
        val name2 = element2.asInstanceOf[ScParameter].getName
        if (name1 != null && name2 != null) {
          return name1.compareTo(name2)
        }
      }
      return 1
    }
  }
}