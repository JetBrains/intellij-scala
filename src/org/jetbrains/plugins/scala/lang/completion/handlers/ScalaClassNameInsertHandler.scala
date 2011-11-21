package org.jetbrains.plugins.scala
package lang.completion.handlers

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import lang.completion.ScalaCompletionUtil
import lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import lang.psi.ScalaPsiUtil
import lang.psi.api.ScalaFile
import lang.resolve.ResolveUtils
import lang.psi.api.toplevel.typedef.ScObject
import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.util.Condition
import com.intellij.psi.{PsiFile, PsiDocumentManager, PsiMember, PsiClass}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaClassNameInsertHandler extends ScalaInsertHandler {
  override def handleInsert(context: InsertionContext, item: LookupElement) {
    val startOffset = context.getStartOffset
    var ref: ScReferenceElement = PsiTreeUtil.findElementOfClassAtOffset(context.getFile, startOffset, classOf[ScReferenceElement], false)
    val useFullyQualifiedName = PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null &&
      PsiTreeUtil.getParentOfType(ref, classOf[ScImportSelectors]) == null //do not complete in sel
    if (ref == null) return
    val file = ref.getContainingFile
    val patchedObject = ScalaCompletionUtil.getScalaLookupObject(item)
    if (patchedObject == null) return

    patchedObject match {
      case ScalaLookupObject(cl: PsiClass, _, _, isInRef) =>
        while (ref.getParent != null && ref.getParent.isInstanceOf[ScReferenceElement] &&
                (ref.getParent.asInstanceOf[ScReferenceElement].qualifier match {
                  case Some(r) => r != ref
                  case _ => true
                }))
          ref = ref.getParent.asInstanceOf[ScReferenceElement]
        val addDot = if (cl.isInstanceOf[ScObject] && isInRef) "." else ""
        val newRef = (useFullyQualifiedName, ref) match {
          case (false, ref: ScReferenceExpression) =>
            ScalaPsiElementFactory.createExpressionFromText(cl.getName + addDot, cl.getManager).asInstanceOf[ScReferenceExpression]
          case (false, _) =>
            ScalaPsiElementFactory.createReferenceFromText(cl.getName + addDot, cl.getManager)
          case (true, _) =>
            ScalaPsiElementFactory.createReferenceFromText(cl.getQualifiedName + addDot, cl.getManager)
        }
        ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
        newRef.bindToElement(cl)
        if (addDot == ".") {
          context.setLaterRunnable(new Runnable {
            def run() {
              AutoPopupController.getInstance(context.getProject).scheduleAutoPopup(
                context.getEditor, new Condition[PsiFile] {
                  def value(t: PsiFile): Boolean = t == context.getFile
                }
              )
            }
          })
        }
      case ScalaLookupObject(namedElement, _, _, _) =>
        val containingClass = ScalaPsiUtil.nameContext(namedElement) match {
          case memb: PsiMember =>
            memb.getContainingClass
          case _ => null
        }
        super.handleInsert(context, item)
        if (containingClass != null) {
          val document = context.getEditor.getDocument
          PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
          file match {
            case scalaFile: ScalaFile =>
              val elem = scalaFile.findElementAt(startOffset)
              val usedQuickfix = item.getUserData(ResolveUtils.usedImportStaticQuickfixKey)
              val shouldImport = item.getUserData(ResolveUtils.shouldImportKey)
              def qualifyReference(ref: ScReferenceExpression) {
                val newRef = ScalaPsiElementFactory.createExpressionFromText(
                  containingClass.getName + "." + ref.getText,
                  containingClass.getManager).asInstanceOf[ScReferenceExpression]
                ref.getNode.getTreeParent.replaceChild(ref.getNode, newRef.getNode)
                newRef.qualifier.get.asInstanceOf[ScReferenceExpression].bindToElement(containingClass)
              }
              elem.getParent match {
                case ref: ScReferenceExpression if usedQuickfix == null || !usedQuickfix.booleanValue() =>
                  if (shouldImport != null && shouldImport.booleanValue()) {
                    qualifyReference(ref)
                  }
                case ref: ScReferenceExpression =>
                  if (shouldImport == null || !shouldImport.booleanValue()) {
                    qualifyReference(ref)
                  } else {
                    //import static
                    ref.bindToElement(namedElement)
                  }
                case _ =>
              }
            case _ =>
          }
        }
      case _ =>
    }
  }
}