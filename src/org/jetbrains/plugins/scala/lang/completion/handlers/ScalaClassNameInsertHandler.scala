package org.jetbrains.plugins.scala
package lang.completion.handlers

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScalaLookupObject
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import lang.completion.ScalaCompletionUtil
import lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaClassNameInsertHandler extends InsertHandler[LookupElement] {
  def handleInsert(context: InsertionContext, item: LookupElement) {
    val startOffset = context.getStartOffset
    var ref: ScReferenceElement = PsiTreeUtil.findElementOfClassAtOffset(context.getFile, startOffset, classOf[ScReferenceElement], false)
    val useFullyQualiedName = PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null &&
      PsiTreeUtil.getParentOfType(ref, classOf[ScImportSelectors]) == null //do not complete in sel
    if (ref == null) return
    val patchedObject = ScalaCompletionUtil.getScalaLookupObject(item)
    if (patchedObject == null) return

    patchedObject match {
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
}