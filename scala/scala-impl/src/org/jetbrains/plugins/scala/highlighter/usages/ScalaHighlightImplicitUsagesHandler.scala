package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement, PsiReference, ReferenceRange}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.usages.ScalaHighlightImplicitUsagesHandler.TargetKind
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGiven, ScGivenDefinition, ScMember}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.ImplicitUtil._

import java.util
import scala.jdk.CollectionConverters._

class ScalaHighlightImplicitUsagesHandler[T](editor: Editor, file: PsiFile, data: T)
                                            (implicit kind: TargetKind[T])
    extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override lazy val getTargets: util.List[PsiElement] = (kind.target(data).toSeq: Seq[PsiElement]).asJava

  override def selectTargets(targets: util.List[_ <: PsiElement],
                             selectionConsumer: Consumer[_ >: util.List[_ <: PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override def computeUsages(targets: util.List[_ <: PsiElement]): Unit = {
    import ScalaHighlightImplicitUsagesHandler._
    val usages = targets.asScala
      .flatMap(findUsages(editor.getProject, file, _))
      .flatMap(ReferenceRange.getAbsoluteRanges(_).asScala)
    val targetIds = targets.asScala.flatMap(nameId)
    myReadUsages.addAll((targetIds ++ usages).asJava)
  }

  override def highlightReferences: Boolean = true

  override def highlightUsages(): Unit = {
    val targets = getTargets
    if (targets.isEmpty) {
      invokeDefaultHandler()
    } else {
      super.highlightUsages()
    }
  }

  //we want to avoid resolve in ScalaHighlightUsagesHandlerFactory, but also not to use ScalaHighlightImplicitUsagesHandler
  //for non-implicit elements
  private def invokeDefaultHandler(): Unit =
    ScalaHighlightUsagesHandlerFactory.implicitHighlightingEnabled.withValue(false) {
      HighlightUsagesHandler.invoke(editor.getProject, editor, file)
    }

  private def nameId(target: PsiElement): Option[TextRange] = target match {
    case target if target.getContainingFile != file =>
      None
    case givenDefinition: ScGivenDefinition =>
      givenDefinition.nameElement
        .orElse(
          givenDefinition.extendsBlock.templateParents
            .flatMap(_.firstParentClause.map(_.typeElement))
        )
        .map(_.getTextRange)
    case named: ScNamedElement =>
      named.nameId.toOption.map(_.getTextRange)
    case _ =>
      None
  }
}

object ScalaHighlightImplicitUsagesHandler {
  trait TargetKind[T] {
    def target(t: T): Option[PsiNamedElement]
  }

  object TargetKind {
    implicit val namedKind: TargetKind[ScNamedElement] = target(_)

    implicit val refKind: TargetKind[ScReference] = ref => ref.bind().flatMap {
      case ScalaResolveResult.ApplyMethodInnerResolve(inner) => target(inner.element)
      case ScalaResolveResult(named: ScNamedElement, _)      => target(named)
      case _                                                 => None
    }

    trait ContextBoundTargetKind extends TargetKind[ScContextBound] {
      def target(t: ScContextBound): Option[ScParameter]
    }
    implicit val contextBoundKind: ContextBoundTargetKind = contextBoundImplicitTarget

    private def target(named: PsiNamedElement): Option[PsiNamedElement] = named match {
      case _ if !named.isValid                             => None
      case c: ScClass                                      => c.getSyntheticImplicitMethod
      case member: ScMember =>
        member.syntheticNavigationElement match {
          case given: ScGivenDefinition =>
            Some(given)
          case given: ScGiven =>
            Some(given)
          case _ if ScalaPsiUtil.isImplicit(member) =>
            Some(member)
          case _ => None
        }
      case n: ScNamedElement if ScalaPsiUtil.isImplicit(n) =>
        Some(n)
      case _ =>
        None
    }

    private def contextBoundImplicitTarget(cb: ScContextBound): Option[ScParameter] = {
      val typeElem = cb.typeElement
      if (!typeElem.isValid) return None

      val typeParam = cb.parentTypeParam match {
        case Some(tp) => tp
        case None => return None
      }
      val methodLike = typeParam.getOwner match {
        case fun: ScFunction => Some(fun)
        case c: ScClass => c.constructor
        case _ => None
      }
      def implicitParams(ml: ScMethodLike) =
        ml.effectiveParameterClauses
          .filter(_.isImplicit)
          .flatMap(_.effectiveParameters)

      val implicits = methodLike.map(implicitParams).getOrElse(Seq.empty)
      typeElem.analog.flatMap { analog =>
        lazy val analogType = analog.calcType
        implicits.find { param =>
          param.typeElement.exists(_.calcType == analogType)
        }
      }
    }
  }

  private def findUsages(project: Project, file: PsiFile, target: PsiElement): Seq[PsiReference] = {
    val useScope = target.getUseScope
    if (!useScope.contains(file.getVirtualFile)) return Seq.empty

    def inUseScope(elem: PsiElement) = useScope match {
      case ls: LocalSearchScope => ls.containsRange(file, elem.getTextRange)
      case _ => true
    }

    file
      .elements(_.isVisible(project, file))
      .filter(inUseScope)
      .flatMap(target.refOrImplicitRefIn)
      .toSeq
  }
}
