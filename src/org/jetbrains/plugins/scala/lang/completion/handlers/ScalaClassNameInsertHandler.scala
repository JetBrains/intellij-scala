package org.jetbrains.plugins.scala
package lang.completion.handlers

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.{PsiDocumentManager, PsiClass}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import lang.psi.api.toplevel.imports.ScImportStmt

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaClassNameInsertHandler extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
    val editor = context.getEditor
    val document = editor.getDocument
    val startOffset = context.getStartOffset
    val lookupStringLength = item.getLookupString.length
    var ref: ScReferenceElement = PsiTreeUtil.findElementOfClassAtOffset(context.getFile, startOffset, classOf[ScReferenceElement], false)
    val useFullyQualiedName = (PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null)
    if (ref == null) return
    item.getObject match {
      case ScalaLookupObject(cl: PsiClass, _, _) =>
        while (ref.getParent != null && ref.getParent.isInstanceOf[ScReferenceElement] &&
                (ref.getParent.asInstanceOf[ScReferenceElement].qualifier match {
                  case Some(r) => r != ref
                  case _ => true
                }))
          ref = ref.getParent.asInstanceOf[ScReferenceElement]
        val newRef = (useFullyQualiedName, ref) match {
          case (false, ref: ScReferenceExpression) =>
            ScalaPsiElementFactory.createExpressionFromText(cl.getName, cl.getManager).asInstanceOf[ScReferenceExpression]
          case (false, _) =>
            ScalaPsiElementFactory.createReferenceFromText(cl.getName, cl.getManager)
          case (true, _) =>
            ScalaPsiElementFactory.createReferenceFromText(cl.getQualifiedName, cl.getManager)
        }
        ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
        newRef.bindToElement(cl)
      case _ =>
    }
  }
  /*def handleInsert(context: InsertionContext, item: JavaPsiClassReferenceElement): ClassNameInsertHandlerResult = {
    var file: PsiFile = context.getFile
    if (!file.isInstanceOf[ScalaFile]) return ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE
    var editor: Editor = context.getEditor
    var endOffset: Int = editor.getCaretModel.getOffset
    if (PsiTreeUtil.findElementOfClassAtOffset(file, endOffset - 1, classOf[ScImportExpr], false) != null) {
      return ClassNameInsertHandlerResult.INSERT_FQN
    }
    var position: PsiElement = file.findElementAt(endOffset - 1)
    var project: Project = context.getProject
    var psiClass: PsiClass = item.getObject
    if (!psiClass.isValid) return AllClassesGetter.ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE
    var qname: String = psiClass.getQualifiedName
    var shortName: String = psiClass.getName
    if (qname == null) return AllClassesGetter.ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE
    val ref = file.findReferenceAt(endOffset - 1)
    if (ref != null) {
      val psiManager: PsiManager = file.getManager
      def checkEquivalence(elem: PsiElement): Boolean = {
        elem match {
          case cl: PsiClass =>
            psiManager.areElementsEquivalent(psiClass, cl)
          case method: PsiMethod if method.isConstructor =>
            psiManager.areElementsEquivalent(psiClass, method.getContainingClass)
          case method: PsiMethod if Set("apply", "unapply", "unapplySeq").contains(method.getName) =>
            psiManager.areElementsEquivalent(psiClass, method.getContainingClass)
          case _ => false
        }
      }
      val element: PsiElement = ref.resolve
      if (checkEquivalence(element)) return ClassNameInsertHandlerResult.REFERENCE_CORRECTED
      if (psiClass.isValid) {
        val newRef = ref.bindToElement(psiClass)
        if (newRef != null) {
          val psiElement: PsiElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newRef)
          if (psiElement != null) {
            for (reference <- psiElement.getReferences) {
              if (checkEquivalence(reference.resolve)) {
                return ClassNameInsertHandlerResult.REFERENCE_CORRECTED
              }
            }
          }
        }
      }
    }
    return ClassNameInsertHandlerResult.INSERT_FQN
  }*/
}