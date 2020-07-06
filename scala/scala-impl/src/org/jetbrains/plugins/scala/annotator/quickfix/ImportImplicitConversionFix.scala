package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.completion.JavaCompletionUtil.isInExcludedPackage
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.annotator.intention.{MemberToImport, ScalaAddImportAction, ScalaImportElementFix}
import org.jetbrains.plugins.scala.extensions.{ChildOf, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitConversion, ImplicitCollector, ImplicitConversionData}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult.containingObject
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

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

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() && ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CONVERSIONS
}

object ImportImplicitConversionFix {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] =
      reference match {
        case refExpr: ScReferenceExpression if refExpr.isQualified                                  => ImportImplicitConversionFix(refExpr).toSeq
        case ChildOf(ScSugarCallExpr(_, refExpr: ScReferenceExpression, _)) if refExpr == reference => ImportImplicitConversionFix(refExpr).toSeq
        case _ => Nil
      }
  }

  def apply(ref: ScReferenceExpression): Option[ImportImplicitConversionFix] = {
    val visible =
      (for {
        result <- ImplicitCollector.visibleImplicits(ref)
        fun    <- result.element.asOptionOf[ScFunctionDefinition]
        obj    <- containingObject(result)
      } yield GlobalImplicitConversion(obj, fun))
        .toSet

    val conversions = for {
      qualifier                <- qualifier(ref).toSeq
      (conversion, resultType) <- ImplicitConversionData.getPossibleConversions(qualifier).toSeq
      if !isInExcludedPackage(conversion.containingObject, false) &&
        !visible.contains(conversion) &&
        CompletionProcessor.variantsWithName(resultType, qualifier, ref.refName).nonEmpty
    } yield conversion

    val sorted = conversions.sortBy(c => (isDeprecated(c), c.qualifiedName))

    if (sorted.isEmpty) None
    else Some(new ImportImplicitConversionFix(ref, sorted))
  }

  private def qualifier(ref: ScReferenceExpression): Option[ScExpression] = ref match {
    case prefix: ScInterpolatedExpressionPrefix =>
      prefix.getParent.asInstanceOf[ScInterpolatedStringLiteral]
        .desugaredExpression.flatMap(_._1.qualifier)
    case ChildOf(ScSugarCallExpr(base, refExpr: ScReferenceExpression, _)) if refExpr == ref =>
      Some(base)
    case _ =>
      ref.qualifier
  }

  private def isDeprecated(conversion: GlobalImplicitConversion): Boolean =
    conversion.containingObject.isDeprecated || conversion.function.isDeprecated
}