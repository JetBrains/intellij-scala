package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.awt.{Graphics, Insets, Rectangle}

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme, EditorFontType}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.{Disposer, Key}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.hints.ScalaExprChainTypeHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Expression

import scala.annotation.tailrec
import scala.collection.JavaConverters._

private[codeInsight] trait ScalaExprChainTypeHintsPass {

  private val settings = ScalaCodeInsightSettings.getInstance

  protected def showExpressionChainType: Boolean = settings.showExpressionChainType
  protected def alignExpressionChain: Boolean = settings.alignExpressionChain
  protected def hideIdenticalTypesInExpressionChain: Boolean = settings.hideIdenticalTypesInExpressionChain

  private var collectedHintTemplates = Seq.empty[Seq[AlignedHintTemplate]]

  def collectExpressionChainTypeHints(editor: Editor, root: PsiElement): Unit = {
    collectedHintTemplates =
      if (editor.isOneLineMode || !showExpressionChainType) Seq.empty
      else (
        for {
          ExprChain(exprChain) <- root.elements
          if exprChain.length >= 3

          exprsAtLineEnd = exprChain.filter(isFollowedByLineEnd)
          if exprsAtLineEnd.length >= 3

          exprs =
            if (Expression(exprsAtLineEnd.head).hasStableType) exprsAtLineEnd.tail
            else exprsAtLineEnd

          types = exprs
            .map(e => e.`type`())
            .takeWhile {
              _.isRight
            }
            .map(_.right.get.tryExtractDesignatorSingleton)
          if types.toSet.size >= 2

          exprsAndTypes =
            if (!hideIdenticalTypesInExpressionChain) exprs.zip(types)
            else removeConsecutiveDuplicates(exprs.zip(types))
        } yield {
          for ((expr, ty) <- exprsAndTypes)
            yield AlignedHintTemplate(textFor(expr, ty, editor), expr)
        }
      ).toList
  }

  def regenerateExprChainHints(editor: Editor, inlayModel: InlayModel, rootElement: PsiElement): Unit = {
    inlayModel
      .getAfterLineEndElementsInRange(rootElement.startOffset, rootElement.endOffset)
      .asScala
      .filter(ScalaExprChainKey.isIn)
      .foreach { inlay =>
        inlay
          .getUserData(ScalaExprChainDisposableKey)
          .toOption
          .foreach(Disposer.dispose)
        Disposer.dispose(inlay)
      }

    assert(collectedHintTemplates.forall(_.nonEmpty))
    val document = editor.getDocument
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
        inlayModel.addInlineElement(hint.expr.endOffset, false, new TextPartsHintRenderer(hint.textParts, None))
      }
    } else if (alignExpressionChain) {
      collectedHintTemplates.foreach(new AlignedInlayGroup(_)(inlayModel, document, charWidth))
    } else {
      for (hints <- collectedHintTemplates; hint <- hints) {
        val inlay = inlayModel.addAfterLineEndElement(
          hint.expr.endOffset,
          false,
          new TextPartsHintRenderer(hint.textParts, typeHintsMenu) {
            override protected def getMargin(editor: Editor): Insets = new Insets(0, charWidth, 0, 0)
          }
        )
        inlay.putUserData(ScalaExprChainKey, true)
      }
    }
  }

  private def textFor(expr: ScExpression, ty: ScType, editor: Editor): Seq[Text] = {
    implicit val scheme: EditorColorsScheme = editor.getColorsScheme
    implicit val tpc: TypePresentationContext = TypePresentationContext(expr)

    Text(": ") +: textPartsOf(ty, settings.presentationLength)
  }
}

private object ScalaExprChainTypeHintsPass {
  private val ScalaExprChainKey = Key.create[Boolean]("SCALA_EXPR_CHAIN_KEY")
  private val ScalaExprChainDisposableKey = Key.create[Disposable]("SCALA_EXPR_CHAIN_DISPOSABLE_KEY")
  private val typeHintsMenu = Some("TypeHintsMenu")

  object ExprChain {
    def unapply(element: PsiElement): Option[Seq[ScExpression]] = {
      element match {
        case expr: ScExpression if isMostOuterExpression(expr) =>
          Some(collectChain(expr))
        case _ => None
      }
    }

    private def isMostOuterExpression(expr: PsiElement): Boolean = {
      expr.getParent match {
        case _: ScReferenceExpression | _: ScMethodCall | _: ScParenthesisedExpr => false
        case _ => true
      }
    }

    private def collectChain(expr: ScExpression): List[ScExpression] = {
      @tailrec
      def collectChainAcc(expr: ScExpression, acc: List[ScExpression]): List[ScExpression] = {
        val newAcc = if (!expr.parent.exists(_.is[ScMethodCall])) expr :: acc else acc
        expr match {
          case ChainCall(inner) => collectChainAcc(inner, newAcc)
          case _ => newAcc
        }
      }
      collectChainAcc(expr, Nil)
    }

    private object ChainCall {
      def unapply(element: PsiElement): Option[ScExpression] = element match {
        case ScReferenceExpression.withQualifier(inner) => Some(inner)
        case MethodInvocation(inner, _) => Some(inner)
        case ScParenthesisedExpr(inner) => Some(inner)
        case _ => None
      }
    }
  }

  def isFollowedByLineEnd(elem: PsiElement): Boolean = {
    elem match {
      case elem if elem.followedByNewLine(ignoreComments = false) =>
        true

      case Parent((ref: ScReferenceExpression) && Parent(mc: ScMethodCall)) =>
        /*
         * Check if we have a situation like
         *
         *  something
         *   .func {         // <- don't add type here
         *
         *   }.func {        // <- add type here (return type of `something.func{...}`)
         *
         *   }.func { x =>   // <- don't add type here
         *
         *   }
         */
        def isArgumentBegin = mc.args.getFirstChild match {
          case blk: ScBlockExpr =>
            blk.getLBrace.exists(_.followedByNewLine(ignoreComments = false))
          case firstElem if firstElem.elementType == ScalaTokenTypes.tLPARENTHESIS =>
            firstElem.followedByNewLine(ignoreComments = false)
          case _ =>
            false
        }

        ref.followedByNewLine(ignoreComments = false) || isArgumentBegin

      case _ =>
        false
    }
  }

  private def removeConsecutiveDuplicates(exprsWithTypes: Seq[(ScExpression, ScType)]): Seq[(ScExpression, ScType)] =
    exprsWithTypes.foldLeft(List.empty[(ScExpression, ScType)]) {
      case (Nil, ewt) => List(ewt)
      case (ls, ewt) if ls.head._2 == ewt._2 => ls
      case (ls, ewt) => ewt :: ls
    }.reverse

  private case class AlignedHintTemplate(textParts: Seq[Text], expr: ScExpression)

  private class AlignedInlayGroup(hints: Seq[AlignedHintTemplate],
                                  minMargin: Int = 1,
                                  maxMargin: Int = 6)
                                 (inlayModel: InlayModel, document: Document, charWidthInPixel: Int) extends Disposable {
    private val minMarginInPixel = minMargin * charWidthInPixel
    private val maxMarginInPixel = maxMargin * charWidthInPixel

    private val alignmentLines: Seq[AlignmentLine] = {
      def lineOf(expr: ScExpression): Int = document.getLineNumber(expr.endOffset)
      val lineToHintMapping = hints.groupBy(hint => lineOf(hint.expr)).mapValues(_.head)
      val lineHasHint = lineToHintMapping.contains _

      val firstLine = 0 max (lineOf(hints.head.expr) - 1)
      val lastLine = document.getLineCount min (lineOf(hints.last.expr) + 1)

      (firstLine to lastLine).flatMap { line =>
        val maybeHint = lineToHintMapping.get(line)
        val maybeOffset = maybeHint match {
          case Some(hint) => Some(hint.expr.endOffset)
          case _ if lineHasHint(line - 1) || lineHasHint(line + 1) => Some(document.getLineEndOffset(line))
          case _ => None
        }
        maybeOffset.map(new AlignmentLine(_, maybeHint)(document))
      }
    }

    private val inlays: Seq[Inlay[AlignedInlayRenderer]] =
      for(line <- alignmentLines; hint <- line.maybeHint) yield {
        val inlay = inlayModel.addAfterLineEndElement(
          hint.expr.endOffset,
          false,
          new AlignedInlayRenderer(line, hint.textParts)
        )
        inlay.putUserData(ScalaExprChainKey, true)
        inlay
      }

    locally {
      inlays.head.putUserData(ScalaExprChainDisposableKey, this)
    }

    private def recalculateGroupsOffsets(editor: Editor): Unit = {
      // unfortunately `AlignedHintsRenderer.getMargin -> recalculateGroupsOffsets`
      // is called by `inlayModel.addAfterLineEndElement` before inlays is actually set
      if (inlays == null)
        return

      val allEndXs = alignmentLines.map(_.lineEndX(editor))
      val actualEndXs = alignmentLines.withFilter(_.hasHint).map(_.lineEndX(editor))
      val max = allEndXs.max
      val avg = actualEndXs.sum / actualEndXs.length
      var targetMaxX = max + math.max(minMarginInPixel, maxMarginInPixel - (max - avg) / 3)

      // this makes the group more stable and less flickery
      targetMaxX -= targetMaxX % charWidthInPixel

      for (inlay <- inlays) {
        val renderer = inlay.getRenderer
        val endX = renderer.line.lineEndX(editor)
        renderer.setMargin(endX, targetMaxX - endX, inlay)
      }
    }

    override def dispose(): Unit = alignmentLines.foreach(_.dispose())

    private class AlignmentLine(offset: Int, val maybeHint: Option[AlignedHintTemplate])(document: Document) extends Disposable {
      private val marker: RangeMarker = document.createRangeMarker(offset, offset)

      def hasHint: Boolean = maybeHint.isDefined

      def lineEndX(editor: Editor): Int = {
        editor.offsetToXY(document.getLineEndOffset(document.getLineNumber(marker.getEndOffset)), true, false).x
      }

      override def dispose(): Unit = marker.dispose()
    }

    private class AlignedInlayRenderer(val line: AlignmentLine, textParts: Seq[Text])
      extends TextPartsHintRenderer(textParts, typeHintsMenu) {

      private case class Cached(lineEndX: Int, margin: Int)
      private var cached: Cached = Cached(0, 0)

      def setMargin(lineEndX: Int, margin: Int, inlay: Inlay[_]): Unit = {
        if (cached.margin != margin) {
          cached = Cached(lineEndX, margin)

          inlay.updateSize()
        }
      }

      override def paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
        if (cached.lineEndX != line.lineEndX(editor)) {
          val oldMargin = cached.margin
          recalculateGroupsOffsets(editor)
          // after recalculating the offset, r has the wrong width, so we fix that here
          r.width += cached.margin - oldMargin
        }

        super.paint(editor, g, r, textAttributes)
      }

      override def getMargin(editor: Editor): Insets = {
        new Insets(0, cached.margin, 0, 0)
      }
    }
  }
}