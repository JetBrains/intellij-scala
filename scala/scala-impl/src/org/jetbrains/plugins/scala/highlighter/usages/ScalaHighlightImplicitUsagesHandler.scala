package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement, PsiReference, ReferenceRange}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.usages.ScalaHighlightImplicitUsagesHandler.TargetKind
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
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
      .flatMap(findUsages(file, _))
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
    case named: ScNamedElement if named.getContainingFile == file => named.nameId.toOption.map(_.getTextRange)
    case _ => None
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

    implicit val contextBoundKind: TargetKind[(ScTypeParam, ScContextBound)] = {
      case (typeParam, cb) => contextBoundImplicitTarget(typeParam, cb.typeElement)
    }

    private def target(named: PsiNamedElement): Option[PsiNamedElement] = named match {
      case _ if !named.isValid                             => None
      case c: ScClass                                      => c.getSyntheticImplicitMethod
      case n: ScNamedElement if ScalaPsiUtil.isImplicit(n) => Option(n)
      case _                                               => None
    }

    private def contextBoundImplicitTarget(typeParam: ScTypeParam, typeElem: ScTypeElement): Option[ScParameter] = {
      if (!typeElem.isValid) return None

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
      implicits.find { param =>
        (param.typeElement, typeElem.analog) match {
          case (Some(t1), Some(t2)) if t1.calcType == t2.calcType => true
          case _                                                  => false
        }
      }
    }
  }

  private def findUsages(file: PsiFile, target: PsiElement): Seq[PsiReference] = {
    val useScope = target.getUseScope
    if (!useScope.contains(file.getVirtualFile)) return Seq.empty

    def inUseScope(elem: PsiElement) = useScope match {
      case ls: LocalSearchScope => ls.containsRange(file, elem.getTextRange)
      case _ => true
    }

    file
      .depthFirst()
      .filter(inUseScope)
      .flatMap(target.refOrImplicitRefIn)
      .toSeq
  }
}
