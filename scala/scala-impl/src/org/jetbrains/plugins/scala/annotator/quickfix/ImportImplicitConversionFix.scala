package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.annotator.intention.{MemberToImport, ScalaAddImportAction, ScalaImportElementFix}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitConversion, ImplicitConversionCache}
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

class ImportImplicitConversionFix private (ref: ScReferenceExpression,
                                           found: Seq[GlobalImplicitConversion])
  extends ScalaImportElementFix(ref) {

  override val elements: Seq[MemberToImport] = found.map(f => MemberToImport(f.function, f.containingObject))

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importImplicitConversion(editor, elements, ref)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = found match {
    case Seq(conversion) => ScalaBundle.message("import.with", conversion.qualifiedName)
    case _               => ScalaBundle.message("import.implicit.conversion")
  }

  override def getFamilyName: String =
    ScalaBundle.message("import.implicit.conversion")
}

object ImportImplicitConversionFix {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] =
      reference match {
        case refExpr: ScReferenceExpression => ImportImplicitConversionFix(refExpr).toSeq
        case _ => Nil
      }
  }

  def apply(ref: ScReferenceExpression): Option[ImportImplicitConversionFix] = {
    val conversions = for {
      qualifier                <- ref.qualifier.toSeq
      (conversion, resultType) <- ImplicitConversionCache(ref.getProject).getPossibleConversions(qualifier).toSeq
      if CompletionProcessor.variantsWithName(resultType, qualifier, ref.refName).nonEmpty
    } yield conversion

    if (conversions.isEmpty) None
    else Some(new ImportImplicitConversionFix(ref, conversions))
  }
}