package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.codeInsight.completion.JavaCompletionUtil.isInExcludedPackage
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.annotator.intention.{MemberToImport, ScalaAddImportAction, ScalaImportElementFix}
import org.jetbrains.plugins.scala.extensions.{ChildOf, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix
import org.jetbrains.plugins.scala.lang.psi.implicits.{GlobalImplicitConversion, ImplicitCollector, ImplicitConversionData}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.collection.mutable.ArrayBuffer

class ImportImplicitConversionFix private (ref: ScReferenceExpression,
                                           found: Seq[GlobalImplicitConversion])
  extends ScalaImportElementFix(ref) {

  override val elements: Seq[MemberToImport] = found.map(f => MemberToImport(f.function, f.owner))

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importImplicitConversion(editor, elements, ref)

  override def isAddUnambiguous: Boolean = false

  override def getTextInner: String = elements match {
    case Seq(conversion) => ScalaBundle.message("import.with", conversion.presentationBody)
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
        case refExpr: ScReferenceExpression if refExpr.isQualified                                  => ImportImplicitConversionFix(refExpr)
        case ChildOf(ScSugarCallExpr(_, refExpr: ScReferenceExpression, _)) if refExpr == reference => ImportImplicitConversionFix(refExpr)
        case _ => Nil
      }
  }

  def apply(ref: ScReferenceExpression): Seq[ScalaImportElementFix] = {
    val visible =
      (for {
        result <- ImplicitCollector.visibleImplicits(ref)
        fun    <- result.element.asOptionOf[ScFunction]
        if fun.isImplicitConversion
      } yield fun)
        .toSet

    val conversionsToImport = ArrayBuffer.empty[GlobalImplicitConversion]
    val notFoundImplicits = ArrayBuffer.empty[ScalaResolveResult]

    for {
      qualifier                 <- qualifier(ref).toSeq
      (conversion, application) <- ImplicitConversionData.getPossibleConversions(qualifier).toSeq

      if !isInExcludedPackage(conversion.owner, false) &&
        CompletionProcessor.variantsWithName(application.resultType, qualifier, ref.refName).nonEmpty

    } {
      val notFoundImplicitParameters = application.implicitParameters.filter(_.isNotFoundImplicitParameter)

      if (visible.contains(conversion.function))
        notFoundImplicits ++= notFoundImplicitParameters
      else if (mayFindImplicits(notFoundImplicitParameters, qualifier))
        conversionsToImport += conversion
    }

    val sortedConversions = conversionsToImport.sortBy(c => (isDeprecated(c), c.qualifiedName))

    val importConversionFix =
      if (sortedConversions.isEmpty) Nil
      else Seq(new ImportImplicitConversionFix(ref, sortedConversions))

    val importMissingImplicitsFixes =
      if (notFoundImplicits.isEmpty) Nil
      else ImportImplicitInstanceFix(notFoundImplicits, ref).toSeq

    importConversionFix ++ importMissingImplicitsFixes
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
    conversion.owner.isDeprecated || conversion.function.isDeprecated

  //todo we already search for implicit parameters, so we could import them together with a conversion
  // need to think about UX
  private def mayFindImplicits(notFoundImplicitParameters: Seq[ScalaResolveResult],
                              owner: ScExpression): Boolean =
    notFoundImplicitParameters.isEmpty || ImportImplicitInstanceFix(notFoundImplicitParameters, owner).nonEmpty
}