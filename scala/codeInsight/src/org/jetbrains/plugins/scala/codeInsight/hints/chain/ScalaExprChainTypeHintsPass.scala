package org.jetbrains.plugins.scala.codeInsight.hints.chain

import java.awt.Insets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorFontType}
import com.intellij.openapi.util.{Disposer, TextRange}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.codeInsight.hints.chain.ScalaExprChainTypeHintsPass._
import org.jetbrains.plugins.scala.codeInsight.hints.textPartsOf
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Expression

import scala.annotation.tailrec
import scala.collection.JavaConverters._

private[codeInsight] trait ScalaExprChainTypeHintsPass {
  private var collectedHintTemplates: Seq[Seq[Hint]] = Seq.empty

  protected def collectExpressionChainTypeHints(editor: Editor, root: PsiElement): Unit = {
    collectedHintTemplates = templatesIn(editor, root)
  }

  protected def regenerateExprChainHints(editor: Editor, inlayModel: InlayModel, rootElement: PsiElement): Unit = {
    disposeHintsIn(inlayModel, rootElement.getTextRange)

    assert(collectedHintTemplates.forall(_.nonEmpty))

    val charWidth = editor
      .getComponent
      .getFontMetrics(EditorColorsManager.getInstance().getGlobalScheme.getFont(EditorFontType.PLAIN))
      .charWidth(' ')

    if (ApplicationManager.getApplication.isUnitTestMode) {
      // there is no way to check for AfterLineEndElements in the test framework
      // so we create normal inline elements here
      // this is ok to test the recognition of expression chain types
      // there is no need to unit test the other alternatives because they need ui tests anyway
      for (hints <- collectedHintTemplates; hint <- hints) {
        inlayModel.addInlineElement(hint.expr.getTextRange.getEndOffset, false, new TextPartsHintRenderer(hint.textParts, None))
      }
    } else if (ScalaCodeInsightSettings.getInstance.alignExpressionChain) {
      collectedHintTemplates.foreach(new Group(_)(inlayModel, editor.getDocument, charWidth))
    } else {
      for (hints <- collectedHintTemplates; hint <- hints) {
        val inlay = inlayModel.addAfterLineEndElement(
          hint.expr.getTextRange.getEndOffset,
          false,
          new TextPartsHintRenderer(hint.textParts, Some(typeHintsMenu)) {
            override protected def getMargin(editor: Editor): Insets = new Insets(0, charWidth, 0, 0)
          }
        )
        inlay.putUserData(ScalaExprChainKey, true)
      }
    }
  }

  private def disposeHintsIn(inlayModel: InlayModel, range: TextRange): Unit = {
    inlayModel
      .getAfterLineEndElementsInRange(range.getStartOffset, range.getEndOffset).asScala
      .filter(ScalaExprChainKey.isIn)
      .foreach { inlay =>
        inlay.getUserData(ScalaExprChainDisposableKey).toOption.foreach(Disposer.dispose)
        Disposer.dispose(inlay)
      }
  }
}

private object ScalaExprChainTypeHintsPass {
  private def templatesIn(editor: Editor, root: PsiElement): Seq[Seq[Hint]] = {
    def settings = ScalaCodeInsightSettings.getInstance

    if (editor.isOneLineMode || !settings.showExpressionChainType) Seq.empty else (
      for {
        element <- root.elements if element.isInstanceOf[ScExpression]
        chain <- chainOf(element.asInstanceOf[ScExpression]) if chain.length >= 3
        exprsAtLineEnd = chain.filter(isFollowedByLineEnd) if exprsAtLineEnd.length >= 3
        exprs = if (Expression(exprsAtLineEnd.head).hasStableType) exprsAtLineEnd.tail else exprsAtLineEnd
        types = exprs.map(e => e.`type`()).takeWhile(_.isRight).map(_.right.get.tryExtractDesignatorSingleton) if types.toSet.size >= 2
        exprsAndTypes = if (!settings.hideIdenticalTypesInExpressionChain) exprs.zip(types) else withoutConsecutiveDuplicates(exprs.zip(types))
      } yield for ((expr, ty) <- exprsAndTypes)
        yield new Hint(Text(": ") +: textPartsOf(ty, settings.presentationLength)(editor.getColorsScheme, TypePresentationContext(expr)), expr)
      ).toList
  }

  private def chainOf(expr: ScExpression): Option[Seq[ScExpression]] =
    if (isMostOuterExpression(expr)) Some(chainOf0(expr)) else None

  private[this] def isMostOuterExpression(expr: PsiElement): Boolean = expr match {
    case Parent(_: ScReferenceExpression | _: ScMethodCall | _: ScParenthesisedExpr) => false
    case _ => true
  }

  private[this] def chainOf0(expr: ScExpression): List[ScExpression] = {
    @tailrec def loop(expr: ScExpression, acc: List[ScExpression]): List[ScExpression] = {
      val newAcc = if (!expr.parent.exists(_.is[ScMethodCall])) expr :: acc else acc
      chainCallOf(expr) match {
        case Some(inner) => loop(inner, newAcc)
        case None => newAcc
      }
    }
    loop(expr, Nil)
  }

  private[this] def chainCallOf(element: PsiElement): Option[ScExpression] = Some(element) collect {
    case ScReferenceExpression.withQualifier(inner) => inner
    case MethodInvocation(inner, _) => inner
    case ScParenthesisedExpr(inner) => inner
  }

  @tailrec private[this] def isFollowedByLineEnd(elem: PsiElement): Boolean = elem.nextSibling match {
    case None => elem.parent match {
      case Some(parent) => isFollowedByLineEnd(parent)
      case None => true
    }
    case Some(ws: PsiWhiteSpace) if hasLineBreak(ws) => true
    case Some(Parent((ref: ScReferenceExpression) && Parent(mc: ScMethodCall))) =>
      /* Check if we have a situation like
       *  something
       *   .func {         // <- don't add type here
       *   }.func {        // <- add type here
       *   }.func { x =>   // <- don't add type here
       *   }
       */
      if (hasLineBreak(ref.getNextSibling)) true else mc.args.getFirstChild match {
        case blk: ScBlockExpr => blk.getLBrace.exists(lb => hasLineBreak(lb.getNextSibling))
        case x => (x.elementType == ScalaTokenTypes.tLPARENTHESIS) && hasLineBreak(x.getNextSibling)
      }
    case _ => false
  }

  private[this] def hasLineBreak(elem: PsiElement): Boolean = elem.asOptionOf[PsiWhiteSpace].exists(_.textContains('\n'))

  private[this] def withoutConsecutiveDuplicates(exprsWithTypes: Seq[(ScExpression, ScType)]): Seq[(ScExpression, ScType)] =
    exprsWithTypes.foldLeft(List.empty[(ScExpression, ScType)]) {
      case (Nil, ewt) => List(ewt)
      case (ls, ewt) => if (ls.head._2 == ewt._2) ls else ewt :: ls
    }.reverse
}