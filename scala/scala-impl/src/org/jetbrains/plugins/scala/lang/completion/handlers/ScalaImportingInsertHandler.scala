package org.jetbrains.plugins.scala
package lang
package completion
package handlers

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

abstract class ScalaImportingInsertHandler(protected val containingClass: PsiClass) extends InsertHandler[ScalaLookupItem] {

  override final def handleInsert(context: InsertionContext,
                                  item: ScalaLookupItem): Unit = {
    new ScalaInsertHandler().handleInsert(context, item)
    context.commitDocument()

    item.findReferenceAtOffset(context) match {
      case reference: ScReferenceExpression if item.shouldImport =>
        qualifyAndImport(reference)
      case reference: ScReferenceExpression =>
        qualifyOnly(reference)
      case _ =>
    }
  }

  protected def qualifyAndImport(reference: ScReferenceExpression): Unit

  protected final def replaceReference(reference: ScReferenceExpression): Unit =
    ScalaInsertHandler.replaceReference(
      reference,
      containingClass.name + "." + reference.getText
    )(containingClass) {
      case ScReferenceExpression.withQualifier(qualifier: ScReferenceExpression) => qualifier
    }

  protected def qualifyOnly(reference: ScReferenceExpression): Unit =
    replaceReference(reference)
}
