package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.codeInsight.intention.{IntentionAction, PriorityAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.autoImport.GlobalMember
import org.jetbrains.plugins.scala.autoImport.GlobalMember.findGlobalMembers
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportElementFix.isExcluded
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{hasImplicitModifier, inNameContext}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.isAccessible
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.autoImport.ImportOrderings.defaultImportOrdering

import scala.jdk.CollectionConverters._

private class ScalaImportGlobalMemberFix(computation: MemberToImportComputation,
                                         ref: ScReferenceExpression) extends ScalaImportElementFix[MemberToImport](ref) {

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction(editor, ref, elements)

  override def isAddUnambiguous: Boolean =
    ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY_METHODS

  override def shouldShowHint(): Boolean =
    super.shouldShowHint() &&
      computation.hasCompatible &&
      ScalaApplicationSettings.getInstance().SHOW_IMPORT_POPUP_STATIC_METHODS

  override def getText: String = elements match {
    case Seq(element) => ScalaBundle.message("import.with", element.qualifiedName)
    case _            => ScalaBundle.message("import.something")
  }

  override def getFamilyName: String = ScalaBundle.message("import.global.member")

  override protected def findElementsToImport(): Seq[MemberToImport] = computation.forImportWithoutPrefix
}

private class ScalaImportGlobalMemberWithPrefixFix(computation: MemberToImportComputation,
                                                   ref: ScReferenceExpression) extends ScalaImportElementFix[MemberToImport](ref) {
  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction.importWithPrefix(editor, elements, ref)

  override def isAddUnambiguous: Boolean = false

  override def shouldShowHint(): Boolean = false

  override def getPriority: PriorityAction.Priority = PriorityAction.Priority.HIGH

  override def getText: String = elements match {
    case Seq(elem) => ScalaBundle.message("import.as", elem.owner.name + "." + elem.name)
    case _         => ScalaBundle.message("import.with.prefix.ellipsis")
  }

  override def getFamilyName: String =
    ScalaBundle.message("import.with.prefix")

  override protected def findElementsToImport(): Seq[MemberToImport] = computation.forImportWithPrefix
}

private class MemberToImportComputation(ref: ScReferenceExpression) {
  private case class Result(withPrefix: Seq[MemberToImport], withoutPrefix: Seq[MemberToImport], hasCompatible: Boolean = false)

  private lazy val result: Result = {
    val allCandidates =
      (findJavaCandidates(ref) ++ findScalaCandidates(ref))
        .toSeq
        .distinctBy(_.qualifiedName)
        .filterNot(c => isExcluded(c.qualifiedName, ref.getProject))
        .sorted(defaultImportOrdering(ref))

    //check for compatibility takes too long if there are that many candidates
    //in this case it's probably better to use qualified reference anyway
    if (allCandidates.size > 30) {
      Result(withPrefix = allCandidates, withoutPrefix = Seq.empty)
    }
    else {
      val compatible = allCandidates.filter(isCompatible(ref, _))
      val candidates = if (compatible.isEmpty) allCandidates else compatible

      Result(
        withPrefix = candidates,
        withoutPrefix = candidates,
        hasCompatible = compatible.nonEmpty
      )
    }
  }

  def forImportWithPrefix: Seq[MemberToImport] = result.withPrefix
  def forImportWithoutPrefix: Seq[MemberToImport] = result.withoutPrefix
  def hasCompatible: Boolean = result.hasCompatible

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

          findGlobalMembers(m, ref.resolveScope)(GlobalMember(_, _, _))
            .map(gm => MemberToImport(gm.named, gm.owner, gm.pathToOwner))
        case _ => None
      }
  }

  private def isStable(m: PsiMethod) =
    ScalaPsiUtil.hasStablePath(m)

  private def isCompatible(originalRef: ScReferenceExpression, candidate: MemberToImport): Boolean = {
    val fixedQualifiedName = ScalaNamesUtil.escapeKeywordsFqn(candidate.qualifiedName)

    createExpressionWithContextFromText(fixedQualifiedName, originalRef.getContext, originalRef) match {
      case qualifiedRef: ScReferenceExpression =>
        qualifiedRef.multiResolveScala(false).exists(_.problems.isEmpty)
      case _ =>
        throw new IllegalStateException(s"Reference is expected to be created from text $fixedQualifiedName")
    }
  }
}

object ScalaImportGlobalMemberFix {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] =
      reference match {
        case refExpr: ScReferenceExpression if !refExpr.isQualified => ScalaImportGlobalMemberFix.create(refExpr)
        case _ => Nil
      }
  }

  private def create(ref: ScReferenceExpression): Seq[IntentionAction] = {
    val computation = new MemberToImportComputation(ref)
    Seq(new ScalaImportGlobalMemberFix(computation, ref), new ScalaImportGlobalMemberWithPrefixFix(computation, ref))
  }

  @TestOnly
  def fixWithoutPrefix(ref: ScReferenceExpression): Option[ScalaImportElementFix[_ <: ElementToImport]] =
    create(ref).findByType[ScalaImportGlobalMemberFix]

  @TestOnly
  def fixWithPrefix(ref: ScReferenceExpression): Option[ScalaImportElementFix[_ <: ElementToImport]] =
    create(ref).findByType[ScalaImportGlobalMemberWithPrefixFix]
}