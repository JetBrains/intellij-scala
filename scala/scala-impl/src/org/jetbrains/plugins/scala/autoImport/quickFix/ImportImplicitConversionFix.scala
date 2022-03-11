package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiDocCommentOwner, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportElementFix.isExcluded
import org.jetbrains.plugins.scala.autoImport.{GlobalExtensionMethod, GlobalImplicitConversion, GlobalMember, GlobalMemberOwner}
import org.jetbrains.plugins.scala.extensions.{ChildOf, ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix
import org.jetbrains.plugins.scala.lang.psi.implicits.{ExtensionMethodData, ImplicitCollector, ImplicitConversionData}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.collection.mutable.ArrayBuffer

class ImportImplicitConversionFix private (ref: ScReferenceExpression,
                                           computation: ConversionToImportComputation)
  extends ScalaImportElementFix[MemberToImport](ref) {

  override protected def findElementsToImport(): Seq[MemberToImport] =
    computation.conversions

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importImplicitConversion(editor, elements, ref)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = elements match {
    case Seq(conversion) => ScalaBundle.message("import.with", conversion.qualifiedName)
    case _               => ScalaBundle.message("import.implicit.conversion")
  }

  override def getFamilyName: String =
    ScalaBundle.message("import.implicit.conversion")

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() && ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_CONVERSIONS
}

private object ImportImplicitConversionFix {
  def apply(ref: ScReferenceExpression, computation: ConversionToImportComputation): ImportImplicitConversionFix =
    new ImportImplicitConversionFix(ref, computation)
}

private class ConversionToImportComputation(ref: ScReferenceExpression) {
  private case class Result(conversions: Seq[MemberToImport],
                            extensionMethods: Seq[ExtensionMethodToImport],
                            missingInstances: Seq[ScalaResolveResult])

  private lazy val result: Result = {
    val visible =
      for {
        result <- ImplicitCollector.visibleImplicits(ref)
        fun    <- result.element.asOptionOf[ScFunction]
        if fun.isImplicitConversion || fun.isExtensionMethod
      } yield fun

    val conversionsToImport = ArrayBuffer.empty[GlobalImplicitConversion]
    val extensionMethodsToImport = ArrayBuffer.empty[GlobalExtensionMethod]
    val notFoundImplicits = ArrayBuffer.empty[ScalaResolveResult]

    def addIfNeeded[GM <: GlobalMember[ScFunction]](globalMember: GM,
                                                    membersToImport: ArrayBuffer[GM],
                                                    implicitParams: Seq[ScalaResolveResult])
                                                   (implicit implicitArgsOwner: ImplicitArgumentsOwner): Unit = {
      val notFoundImplicitParameters = implicitParams.filter(_.isNotFoundImplicitParameter)

      if (visible.contains(globalMember.member))
        notFoundImplicits ++= notFoundImplicitParameters
      else if (mayFindImplicits(notFoundImplicitParameters, implicitArgsOwner))
        membersToImport += globalMember
    }

    qualifier(ref).foreach { implicit qualifier =>
      for {
        (conversion, application) <- ImplicitConversionData.getPossibleConversions(qualifier)
        if !isExcluded(conversion.qualifiedName, ref.getProject) &&
          CompletionProcessor.variantsWithName(application.resultType, qualifier, ref.refName).nonEmpty
      } addIfNeeded(conversion, conversionsToImport, application.implicitParameters)

      for {
        (extensionMethod, application) <- ExtensionMethodData.getPossibleExtensionMethods(qualifier)
        if !isExcluded(extensionMethod.qualifiedName, ref.getProject) &&
          ref.refName == extensionMethod.function.name
      } addIfNeeded(extensionMethod, extensionMethodsToImport, application.implicitParameters)
    }

    val sortedConversions = sortAndMapMembers(conversionsToImport, MemberToImport(_, _, _))
    val sortedExtensionMethods = sortAndMapMembers(extensionMethodsToImport, ExtensionMethodToImport(_, _, _))

    Result(sortedConversions, sortedExtensionMethods, notFoundImplicits.toSeq)
  }

  def conversions: Seq[MemberToImport] = result.conversions
  def extensionMethods: Seq[ExtensionMethodToImport] = result.extensionMethods
  def missingImplicits: Seq[ScalaResolveResult] = result.missingInstances

  private def sortAndMapMembers[GM <: GlobalMember[ScFunction], E <: ElementToImport](members: ArrayBuffer[GM],
                                                                                      constructor: (ScFunction, GlobalMemberOwner, String) => E) =
    members
      .sortBy(e => (isDeprecated(e), e.qualifiedName))
      .toSeq
      .map(e => constructor(e.member, e.owner, e.pathToOwner))

  private def qualifier(ref: ScReferenceExpression): Option[ScExpression] = ref match {
    case prefix: ScInterpolatedExpressionPrefix =>
      prefix.getParent.asInstanceOf[ScInterpolatedStringLiteral]
        .desugaredExpression.flatMap(_._1.qualifier)
    case ChildOf(ScSugarCallExpr(base, refExpr: ScReferenceExpression, _)) if refExpr == ref =>
      Some(base)
    case _ =>
      ref.qualifier
  }

  private def isDeprecated[GM <: GlobalMember[ScFunction]](globalMember: GM): Boolean =
    isDeprecated(globalMember.owner.element) || isDeprecated(globalMember.member)

  private def isDeprecated(element: PsiElement): Boolean = element match {
    case named: PsiNamedElement => named.nameContext match {
      case member: PsiDocCommentOwner => member.isDeprecated
      case _ => false
    }
    case _ => false
  }

  //todo we already search for implicit parameters, so we could import them together with a conversion
  // need to think about UX
  private def mayFindImplicits(notFoundImplicitParameters: Seq[ScalaResolveResult],
                               owner: ImplicitArgumentsOwner): Boolean =
    notFoundImplicitParameters.isEmpty || ImportImplicitInstanceFix.implicitsToImport(notFoundImplicitParameters, owner).nonEmpty
}

object ImportImplicitConversionFixes {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] =
      reference match {
        case refExpr: ScReferenceExpression if refExpr.isQualified                                  => ImportImplicitConversionFixes(refExpr)
        case ChildOf(ScSugarCallExpr(_, refExpr: ScReferenceExpression, _)) if refExpr == reference => ImportImplicitConversionFixes(refExpr)
        case _ => Nil
      }
  }

  def apply(ref: ScReferenceExpression): Seq[ScalaImportElementFix[_ <: ElementToImport]] = {
    val computation = new ConversionToImportComputation(ref)
    Seq(
      ImportImplicitConversionFix(ref, computation),
      ImportExtensionMethodFix(ref, computation),
      ImportImplicitInstanceFix(() => computation.missingImplicits, ref)
    )
  }
}