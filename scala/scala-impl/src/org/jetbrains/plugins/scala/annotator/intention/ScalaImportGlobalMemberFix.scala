package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.codeInsight.completion.JavaCompletionUtil.isInExcludedPackage
import com.intellij.codeInsight.intention.{IntentionAction, PriorityAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.extensions.{&&, ObjectExt, PsiElementExt, PsiMemberExt, PsiNamedElementExt, SeqExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{hasImplicitModifier, inNameContext}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.isAccessible
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.OrderingUtil.orderingByRelevantImports

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

private class ScalaImportGlobalMemberFix(override val elements: Seq[MemberToImport],
                                         ref: ScReferenceExpression,
                                         candidatesAreCompatible: Boolean) extends ScalaImportElementFix(ref) {

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction(editor, ref, elements)

  override def isAddUnambiguous: Boolean =
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() &&
      candidatesAreCompatible &&
      ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_STATIC_METHODS

  override protected def getTextInner: String = elements match {
    case Seq(element) => ScalaBundle.message("import.with", element.presentationBody)
    case _            => ScalaBundle.message("import.something")
  }

  override def getFamilyName: String = ScalaBundle.message("import.global.member")
}

private class ScalaImportGlobalMemberWithPrefixFix(override val elements: Seq[MemberToImport],
                                                   ref: ScReferenceExpression) extends ScalaImportElementFix(ref) {
  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importWithPrefix(editor, elements, ref)

  override def isAddUnambiguous: Boolean = false

  override def shouldShowHint(): Boolean = false

  override def getPriority: PriorityAction.Priority = PriorityAction.Priority.HIGH

  override def getTextInner: String = elements match {
    case Seq(elem) => ScalaBundle.message("import.as", elem.owner.name + "." + elem.name)
    case _         => ScalaBundle.message("import.with.prefix.ellipsis")
  }

  override def getFamilyName: String =
    ScalaBundle.message("import.with.prefix")
}

object ScalaImportGlobalMemberFix {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] =
      reference match {
        case refExpr: ScReferenceExpression if !refExpr.isQualified => ScalaImportGlobalMemberFix.create(refExpr)
        case _ => Nil
      }
  }

  @TestOnly
  def fixWithoutPrefix(ref: ScReferenceExpression): Option[ScalaImportElementFix] =
    create(ref).findByType[ScalaImportGlobalMemberFix]

  @TestOnly
  def fixWithPrefix(ref: ScReferenceExpression): Option[ScalaImportElementFix] =
    create(ref).findByType[ScalaImportGlobalMemberWithPrefixFix]

  private def create(ref: ScReferenceExpression): Seq[IntentionAction] = {
    val allCandidates =
      (findJavaCandidates(ref) ++ findScalaCandidates(ref))
        .toSeq
        .distinctBy(_.qualifiedName)
        .sortBy(_.qualifiedName)(orderingByRelevantImports(ref))

    //check for compatibility takes too long if there are that many candidates
    //in this case it's probably better to use qualified reference anyway
    if (allCandidates.size > 30) {
      Seq(new ScalaImportGlobalMemberWithPrefixFix(allCandidates, ref))
    }
    else {
      val compatible = allCandidates.filter(c => !isInExcludedPackage(c.owner, false) && isCompatible(ref, c))
      val candidates = if (compatible.isEmpty) allCandidates else compatible

      if (candidates.isEmpty) Seq.empty
      else {
        val withoutPrefixFix = new ScalaImportGlobalMemberFix(candidates, ref, compatible.nonEmpty)
        val withPrefixFix = new ScalaImportGlobalMemberWithPrefixFix(candidates, ref)
        Seq(withoutPrefixFix, withPrefixFix)
      }
    }
  }

  private def findJavaCandidates(ref: ScReferenceExpression): Iterable[MemberToImport] =
    JavaStaticMemberNameIndex.getInstance().getStaticMembers(ref.refName, ref.getProject, ref.resolveScope)
      .asScala
      .toSeq
      .flatMap {
        case m: PsiMethod if isStable(m) && isAccessible(m, ref) => Some(MemberToImport(m, m.containingClass))
        case _ => None
      }

  private def findScalaCandidates(ref: ScReferenceExpression): Iterable[MemberToImport] = {
    implicit val project: Project = ref.getProject

    val functions = ScalaIndexKeys.METHOD_NAME_KEY.elements(ref.refName, ref.resolveScope)
    val properties = ScalaIndexKeys.PROPERTY_NAME_KEY.elements(ref.refName, ref.resolveScope).flatMap(_.declaredElements)
    (functions ++ properties)
      .flatMap {
        case (td: ScTypedDefinition) && inNameContext(m: ScMember)
          if isAccessible(m, ref) && !hasImplicitModifier(m) =>

          (m.containingClass.asOptionOf[ScObject] ++: findInheritorObjectsForOwner(m))
            .filter(_.isStable)
            .map(MemberToImport(td, _))
        case _ => None
      }
  }

  private def isStable(m: PsiMethod) =
    ScalaPsiUtil.hasStablePath(m)

  private def isCompatible(originalRef: ScReferenceExpression, candidate: MemberToImport): Boolean = {
    val fixedQualifiedName = ScalaNamesUtil.escapeKeywordsFqn(candidate.qualifiedName)
    val qualifiedRef =
      ScalaPsiElementFactory.createExpressionWithContextFromText(fixedQualifiedName, originalRef.getContext, originalRef)
        .asInstanceOf[ScReferenceExpression]

    qualifiedRef.multiResolveScala(false).exists(_.problems.isEmpty)
  }
}