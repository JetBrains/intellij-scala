package org.jetbrains.plugins.scala.highlighter.usages

import java.util

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class ScalaHighlightImplicitUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement)
    extends HighlightUsagesHandlerBase[PsiElement](editor, file) {

  @tailrec
  private def refNameId(expr: PsiElement): PsiElement = expr match {
    case mc: ScMethodCall if mc.getFirstChild.isInstanceOf[ScParenthesisedExpr] => mc.getFirstChild
    case MethodRepr(_: ScMethodCall, Some(base), None, _)                       => refNameId(base)
    case MethodRepr(_, _, Some(ref), _)                                         => ref.nameId
    case _                                                                      => expr
  }

  override def getTargets: util.List[PsiElement] = util.Collections.singletonList(target)

  override def selectTargets(targets: util.List[PsiElement], selectionConsumer: Consumer[util.List[PsiElement]]): Unit =
    selectionConsumer.consume(targets)

  override def computeUsages(targets: util.List[PsiElement]): Unit = {
    import ScalaHighlightImplicitUsagesHandler._
    val usages = findUsages(file, targets.asScala).map { e =>
      val nameRange = refNameId(e).getTextRange
      TextRange.create(nameRange.getStartOffset, e.getTextOffset + e.getTextLength)
    }
    myReadUsages.addAll(usages.asJava)
  }

  override def highlightReferences: Boolean = true
}

object ScalaHighlightImplicitUsagesHandler {
  private implicit class ImplicitTarget(target: PsiElement) {
    def isImplicitConversionOf(e: ScExpression): Boolean =
      e.getTypeAfterImplicitConversion() match {
        case ExpressionTypeResult(Success(_, _), _, Some(implicitFunction)) =>
          implicitFunction match {
            case `target`                                                                 => true
            case f: ScFunction if f.getSyntheticNavigationElement.contains(target)        => true
            case f: ScExpression if isImplicitParameterOf(f) || isImplicitConversionOf(f) => true
            case _                                                                        => false
          }
        case _ => false
      }

    def isImplicitParameterOf(e: ScExpression): Boolean = {
      def matches(srrs: Seq[ScalaResolveResult]): Boolean = srrs.exists(
        srr => srr.element.getNavigationElement == target.getNavigationElement || matches(srr.implicitParameters)
      )
      matches(e.findImplicitParameters.getOrElse(Seq.empty))
    }
  }

  def findUsages(file: PsiFile, targets: Seq[PsiElement]): Seq[PsiElement] = {
    def matches(e: ScExpression) = targets.exists {
      case Reference(t) => t.isImplicitParameterOf(e) || t.isImplicitConversionOf(e)
      case _            => false
    }

    file
      .depthFirst()
      .collect { case e: ScExpression if matches(e) => e }
      .toSeq
  }
}

/** An element that references some other element.
 * Besides just ScReferenceElement, colon in context bounds is considered a reference to the corresponding evidence.
 */
object Reference {
  def unapply(e: PsiElement): Option[PsiElement] = e match {
    case ref: ScReferenceElement => Some(ref.resolve)
    case rr: ScalaResolveResult  => Some(rr.element)
    case se: ScalaPsiElement     => Some(se)
    // As this handler is only used for ScalaPsiElements and colons (see ScalaHighlightUsagesHandlerFactory),
    // `colon` is actually required to be a colon
    case colon: LeafPsiElement =>
      PsiTreeUtil.getParentOfType(colon, classOf[ScFunction]) match {
        case null => None
        case fn =>
          val parameterClauses = fn.effectiveParameterClauses
          val implicitParams   = parameterClauses.filter(_.isImplicit).flatMap(_.effectiveParameters)
          val targetType       = colon.getNextSiblingNotWhitespaceComment
          targetType match {
            case t: ScTypeElement =>
              val evidences = implicitParams.filter { param =>
                (param.typeElement, t.analog) match {
                  case (Some(t1), Some(t2)) if t1.calcType == t2.calcType => true
                  case _                                                  => false
                }
              }
              if (evidences.length == 1) Some(evidences.head)
              else None
            case _ => None
          }
      }
    case _ => None
  }
}
