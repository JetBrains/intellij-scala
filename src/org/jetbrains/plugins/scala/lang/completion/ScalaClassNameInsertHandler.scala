package org.jetbrains.plugins.scala.lang.completion

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.editor.Editor
//import com.intellij.codeInsight.completion.AllClassesGetter.{ClassNameInsertHandlerResult, ClassNameInsertHandler}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInsight.completion.{DefaultInsertHandler, AllClassesGetter, JavaPsiClassReferenceElement, InsertionContext}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaClassNameInsertHandler /*extends ClassNameInsertHandler {
  def handleInsert(context: InsertionContext, item: JavaPsiClassReferenceElement): ClassNameInsertHandlerResult = {
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
  }
}*/