package org.jetbrains.plugins.scala.codeInsight.hints
package methodChains

import java.awt.Insets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme, EditorFontType}
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.{AnnotatorHints, Text}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.ScalaMethodChainInlayHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Expression

import scala.annotation.tailrec
import scala.collection.JavaConverters._

private[codeInsight] trait ScalaMethodChainInlayHintsPass {

  private val settings = ScalaCodeInsightSettings.getInstance

  protected def showMethodChainInlayHints: Boolean = settings.showMethodChainInlayHints
  protected def alignMethodChainInlayHints: Boolean = settings.alignMethodChainInlayHints
  protected def uniqueTypesToShowMethodChains: Int = settings.uniqueTypesToShowMethodChains
  protected def showObviousTypes: Boolean = settings.showObviousType

  private var collectedHintTemplates = Seq.empty[Seq[AlignedHintTemplate]]

  def collectMethodChainHints(editor: Editor, root: PsiElement): Unit = {
    val minChainCount = math.max(2, uniqueTypesToShowMethodChains)
    collectedHintTemplates =
      if (editor.isOneLineMode || !showMethodChainInlayHints) Seq.empty
      else (
        for {
          MethodChain(methodChain) <- root.elements
          if methodChain.length >= uniqueTypesToShowMethodChains

          methodsAtLineEnd = methodChain.filter(isFollowedByLineEnd)

          if methodsAtLineEnd.length >= minChainCount

          methodsWithoutLast =
            if (alignMethodChainInlayHints || methodsAtLineEnd.last != methodChain.last) methodsAtLineEnd
            else methodsAtLineEnd.init

          methodsWithoutFirst = {
            val dontShowFirst = isSimpleReference(methodsWithoutLast.head) || hasStableType(methodsAtLineEnd.head)

            if (alignMethodChainInlayHints || !dontShowFirst) methodsWithoutLast
            else methodsWithoutLast.tail
          }

          methodAndTypes = methodsWithoutFirst
            .map(m => m -> m.`type`())
            .takeWhile {
              _._2.isRight
            }
            .map { case (m, ty) => m -> ty.right.get.tryExtractDesignatorSingleton }

          methodAndTypesWithoutObviousReturns =
            if (alignMethodChainInlayHints || showObviousTypes) methodAndTypes
            else methodAndTypes.filterNot(hasObviousReturnType)

          if methodAndTypesWithoutObviousReturns.length >= minChainCount

          uniqueTypeCount = methodAndTypesWithoutObviousReturns.map { case (m, ty) => ty.presentableText(m) }.toSet.size
          if uniqueTypeCount >= uniqueTypesToShowMethodChains
        } yield {
          for ((expr, ty) <- methodAndTypesWithoutObviousReturns)
            yield AlignedHintTemplate(textFor(expr, ty, editor), expr)
        }
      ).toList
  }

  def regenerateMethodChainHints(editor: Editor, inlayModel: InlayModel, rootElement: PsiElement): Unit = {
    inlayModel
      .getAfterLineEndElementsInRange(rootElement.startOffset, rootElement.endOffset)
      .asScala
      .filter(ScalaMethodChainKey.isIn)
      .foreach { inlay =>
        AlignedInlayGroup.dispose(inlay)
        Disposer.dispose(inlay)
      }

    assert(collectedHintTemplates.forall(_.nonEmpty))

    // don't show inlay behind the outer most expression when it has an error
    val hintTemplates = collectedHintTemplates
      .map(removeLastIfHasTypeMismatch)
      .filter(_.length >= 2)

    val document = editor.getDocument
    val charWidth = editor
      .getComponent
      .getFontMetrics(EditorColorsManager.getInstance().getGlobalScheme.getFont(EditorFontType.PLAIN))
      .charWidth(' ')

    if (ApplicationManager.getApplication.isUnitTestMode) {
      // there is no way to check for AfterLineEndElements in the test framework
      // so we create normal inline elements here
      // this is ok to test the recognition of method chain inlay hints
      // there is no need to unit test the other alternatives because they need ui tests anyway
      generateInlineHints(hintTemplates, inlayModel)
    } else if (alignMethodChainInlayHints) {
      generateAlignedHints(hintTemplates, document, charWidth, inlayModel)
    } else {
      generateUnalignedHints(hintTemplates, charWidth, inlayModel)
    }
  }

  private def generateInlineHints(hintTemplates: Seq[Seq[AlignedHintTemplate]], inlayModel: InlayModel): Unit =
    for (hints <- hintTemplates; hint <- hints) {
      inlayModel.addInlineElement(hint.expr.endOffset, false, new TextPartsHintRenderer(hint.textParts, None))
    }

  private def generateAlignedHints(hintTemplates: Seq[Seq[AlignedHintTemplate]], document: Document, charWidth: Int, inlayModel: InlayModel): Unit =
    hintTemplates.foreach(new AlignedInlayGroup(_)(inlayModel, document, charWidth))

  private def generateUnalignedHints(hintTemplates: Seq[Seq[AlignedHintTemplate]], charWidth: Int, inlayModel: InlayModel): Unit =
    for (hints <- hintTemplates; hint <- hints) {
      val inlay = inlayModel.addAfterLineEndElement(
        hint.expr.endOffset,
        false,
        new TextPartsHintRenderer(hint.textParts, typeHintsMenu) {
          override protected def getMargin(editor: Editor): Insets = new Insets(0, charWidth, 0, 0)
        }
      )
      inlay.putUserData(ScalaMethodChainKey, true)
    }

  private def textFor(expr: ScExpression, ty: ScType, editor: Editor): Seq[Text] = {
    implicit val scheme: EditorColorsScheme = editor.getColorsScheme
    implicit val tpc: TypePresentationContext = TypePresentationContext(expr)

    Text(": ") +: textPartsOf(ty, settings.presentationLength)
  }
}

private object ScalaMethodChainInlayHintsPass {
  private def isFollowedByLineEnd(elem: PsiElement): Boolean = {
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

  private def hasObviousReturnType(methodAndTypes: (ScExpression, ScType)): Boolean = {
    @tailrec
    def methodName(expr: ScExpression): String = expr match {
      case ref: ScReferenceExpression => ref.refName
      case invoc: MethodInvocation => methodName(invoc.getEffectiveInvokedExpr)
      case ScParenthesisedExpr(inner) => methodName(inner)
      case _ => ""
    }

    val (expr, ty) = methodAndTypes
    isTypeObvious("", ty.presentableText(expr), methodName(expr))
  }

  private def removeLastIfHasTypeMismatch(methodsWithTypes: Seq[AlignedHintTemplate]): Seq[AlignedHintTemplate] = {
    val outermostExpr = methodsWithTypes.last.expr
    if (hasTypeMismatch(outermostExpr)) methodsWithTypes.init
    else methodsWithTypes
  }

  private def hasTypeMismatch(expr: ScExpression): Boolean = AnnotatorHints.in(expr).nonEmpty

  @tailrec
  private def isSimpleReference(expr: ScExpression): Boolean = expr match {
    case ref: ScReferenceExpression => !ref.isQualified
    case ScParenthesisedExpr(inner) => isSimpleReference(inner)
    case _ => false
  }

  @tailrec
  private def hasStableType(expr: ScExpression): Boolean = expr match {
    case ScParenthesisedExpr(inner) => hasStableType(inner)
    case _ => Expression(expr).hasStableType
  }
}