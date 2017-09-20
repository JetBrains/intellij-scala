package org.jetbrains.plugins.scala.highlighter.usages

import java.util

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class ScalaHighlightImplicitUsagesHandler(editor: Editor, file: PsiFile, target: ScNamedElement)
    extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  override def getTargets: util.List[PsiElement] = util.Collections.singletonList(target)

  override def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override def computeUsages(targets: util.List[PsiElement]): Unit = {
    import ScalaHighlightImplicitUsagesHandler._
    val usages = targets.asScala
      .flatMap(findUsages(file, _))
      .map(range)
    myReadUsages.addAll(usages.asJava)
  }

  override def highlightReferences: Boolean = true
}

object ScalaHighlightImplicitUsagesHandler {
  private implicit class ImplicitTarget(target: PsiElement) {

    private def isTarget(named: PsiNamedElement): Boolean = named match {
      case `target`                                                          => true
      case f: ScFunction if f.getSyntheticNavigationElement.contains(target) => true
      case _                                                                 => false
    }

    def isImplicitConversionOf(e: ScExpression): Boolean = {
      e.getTypeAfterImplicitConversion() match {
        case ExpressionTypeResult(Success(_, _), _, Some(implicitFunction)) => isTarget(implicitFunction)
        case _ => e.implicitElement().exists(isTarget)
      }
    }

    def isImplicitParameterOf(e: ImplicitParametersOwner): Boolean = {
      def matches(srrs: Seq[ScalaResolveResult]): Boolean = srrs.exists(
        srr => srr.element.getNavigationElement == target.getNavigationElement || matches(srr.implicitParameters)
      )
      matches(e.findImplicitParameters.getOrElse(Seq.empty))
    }
  }

  def findUsages(file: PsiFile, target: PsiElement): Seq[PsiElement] = {
    val useScope = target.getUseScope
    if (!useScope.contains(file.getVirtualFile)) return Seq.empty

    def inUseScope(elem: PsiElement) = useScope match {
      case ls: LocalSearchScope => ls.containsRange(file, elem.getTextRange)
      case _ => true
    }

    def containsImplicitRef(elem: PsiElement): Boolean = elem match {
      case e: ScExpression if target.isImplicitParameterOf(e) || target.isImplicitConversionOf(e) => true
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