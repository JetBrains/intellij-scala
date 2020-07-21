package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

abstract class ScalaImportingInsertHandler(protected val containingClass: PsiClass)
  extends InsertHandler[LookupElement] {

  override final def handleInsert(context: InsertionContext,
                                  item: LookupElement): Unit = item match {
    case item: ScalaLookupItem =>
      new ScalaInsertHandler().handleInsert(context, item)
      context.commitDocument()

      item.findReferenceAtOffset(context) match {
        case reference: ScReferenceExpression if item.shouldImport =>
          qualifyAndImport(reference)
        case reference: ScReferenceExpression =>
          qualifyOnly(reference)
        case _ =>
      }
    case _ => throw new IllegalArgumentException(s"$item has unexpected type ${item.getClass}")
  }

  protected def qualifyAndImport(reference: ScReferenceExpression): Unit

  protected def qualifyOnly(reference: ScReferenceExpression): Unit =
    qualifyReference(reference)

  private /*final*/ def qualifyReference(reference: ScReferenceExpression): Unit =
    ScalaInsertHandler.replaceReference(
      reference,
      containingClass.name + "." + reference.getText
    )(containingClass) {
      case ScReferenceExpression.withQualifier(qualifier: ScReferenceExpression) => qualifier
    }
}

object ScalaImportingInsertHandler {

  class WithBinding(private val targetElement: PsiNamedElement,
                    override protected val containingClass: PsiClass)
    extends ScalaImportingInsertHandler(containingClass) {

    override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
      bindToTargetElement(reference)

    private /*final*/ def bindToTargetElement(reference: ScReferenceExpression): Unit =
      reference.bindToElement(
        targetElement,
        Some(containingClass)
      )
  }
}
