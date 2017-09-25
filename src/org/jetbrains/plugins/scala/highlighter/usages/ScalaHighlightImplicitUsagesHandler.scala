package org.jetbrains.plugins.scala.highlighter.usages

import java.util

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandler, HighlightUsagesHandlerBase}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.usages.ScalaHighlightImplicitUsagesHandler.TargetKind
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScMethodLike, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class ScalaHighlightImplicitUsagesHandler[T](editor: Editor, file: PsiFile, data: T)
                                            (implicit kind: TargetKind[T])
    extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override def getTargets: util.List[PsiElement] = kind.targets(data).asJava

  override def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override def computeUsages(targets: util.List[PsiElement]): Unit = {
    import ScalaHighlightImplicitUsagesHandler._
    val usages = targets.asScala
      .flatMap(findUsages(file, _))
      .map(range)
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
  private def invokeDefaultHandler(): Unit = {
    ScalaHighlightUsagesHandlerFactory.implicitHighlightingEnabled.set(false)
    try {
      HighlightUsagesHandler.invoke(editor.getProject, editor, file)
    } finally {
      ScalaHighlightUsagesHandlerFactory.implicitHighlightingEnabled.set(true)
    }
  }

  private def nameId(target: PsiElement): Option[TextRange] = target match {
    case named: ScNamedElement if named.getContainingFile == file => named.nameId.toOption.map(_.getTextRange)
    case _ => None
  }
}

object ScalaHighlightImplicitUsagesHandler {
  trait TargetKind[T] {
    def targets(t: T): Seq[PsiElement]
  }

  object TargetKind {
    implicit val namedKind: TargetKind[ScNamedElement] = targets(_)

    implicit val refKind: TargetKind[ScReferenceElement] = ref => ref.resolve match {
      case named: ScNamedElement => targets(named)
      case _ => Seq.empty
    }

    implicit val contextBoundKind: TargetKind[(ScTypeParam, ScTypeElement)] = {
      case (typeParam, typeElem) => contextBoundImplicitTarget(typeParam, typeElem)
    }

    private def targets(named: ScNamedElement): Seq[PsiElement] = {
      if (!named.isValid) return Seq.empty

      named match {
        case c: ScClass => c.getSyntheticImplicitMethod.toSeq
        case n: ScNamedElement if ScalaPsiUtil.isImplicit(n) => Seq(n)
        case n => Seq.empty
      }
    }

    private def contextBoundImplicitTarget(typeParam: ScTypeParam, typeElem: ScTypeElement): Seq[ScParameter] = {
      if (!typeElem.isValid) return Seq.empty

      val typeParam = typeElem.getParent.asInstanceOf[ScTypeParam]
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
      implicits.filter { param =>
        (param.typeElement, typeElem.analog) match {
          case (Some(t1), Some(t2)) if t1.calcType == t2.calcType => true
          case _                                                  => false
        }
      }
    }
  }

  private implicit class ImplicitTarget(target: PsiElement) {

    private def isTarget(named: PsiNamedElement): Boolean = named match {
      case `target`                                                          => true
      case f: ScFunction if f.getSyntheticNavigationElement.contains(target) => true
      case _                                                                 => false
    }

    private def matches(srr: ScalaResolveResult): Boolean = {
      isTarget(srr.element) ||
        srr.implicitParameters.exists(matches) ||
        srr.implicitConversion.exists(matches)
    }

    def isImplicitConversionOrParameter(e: ScExpression): Boolean = {
      e.implicitConversion().exists(matches) || isImplicitParameterOf(e)
    }

    def isImplicitParameterOf(e: ImplicitParametersOwner): Boolean =
      e.findImplicitParameters
        .getOrElse(Seq.empty)
        .exists(matches)
  }

  private def findUsages(file: PsiFile, target: PsiElement): Seq[PsiElement] = {
    val useScope = target.getUseScope
    if (!useScope.contains(file.getVirtualFile)) return Seq.empty

    def inUseScope(elem: PsiElement) = useScope match {
      case ls: LocalSearchScope => ls.containsRange(file, elem.getTextRange)
      case _ => true
    }

    def containsImplicitRef(elem: PsiElement): Boolean = elem match {
      case e: ScExpression if target.isImplicitConversionOrParameter(e) => true
      case st: ScSimpleTypeElement if target.isImplicitParameterOf(st) => true
      case _ => false
    }

    file
      .depthFirst()
      .filter(e => inUseScope(e) && containsImplicitRef(e))
      .toSeq
  }

  @tailrec
  private def range(usage: PsiElement): TextRange = {
    val simpleRange = usage.getTextRange
    def startingFrom(elem: PsiElement): TextRange = {
      val start = elem.getTextRange.getStartOffset
      TextRange.create(start, simpleRange.getEndOffset)
    }
    def forTypeElem(typeElem: ScSimpleTypeElement) = {
      def newTd =
        Option(PsiTreeUtil.getParentOfType(typeElem, classOf[ScNewTemplateDefinition]))
          .filter(_.constructor.flatMap(_.simpleTypeElement).contains(typeElem))

      def constructor =
        Option(PsiTreeUtil.getParentOfType(typeElem, classOf[ScConstructor]))
          .filter(_.simpleTypeElement.contains(typeElem))

      newTd
        .orElse(constructor)
        .getOrElse(typeElem)
        .getTextRange
    }

    usage match {
      case ScMethodCall(ScParenthesisedExpr(_), _)          => simpleRange
      case ScMethodCall(_: ScThisReference, _)              => simpleRange
      case MethodRepr(_: ScMethodCall, Some(base), None, _) => range(base)
      case MethodRepr(_, _, Some(ref), _)                   => startingFrom(ref.nameId)
      case simpleTypeElem: ScSimpleTypeElement              => forTypeElem(simpleTypeElem)
      case _                                                => simpleRange
    }
  }

}