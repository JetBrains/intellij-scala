package org.jetbrains.plugins.scala.lang.completion.handlers

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiClass
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
    case _ => item.handleInsert(context)
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
