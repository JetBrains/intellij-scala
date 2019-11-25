package org.jetbrains.plugins.scala.codeInsight.hints
package methodChains

import java.awt.Insets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme, EditorFontType}
import com.intellij.openapi.util.Disposer
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.annotator.hints.{AnnotatorHints, Text}
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.ScalaMethodChainInlayHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Expression

import scala.annotation.tailrec
import scala.collection.JavaConverters._

private[codeInsight] trait ScalaMethodChainInlayHintsPass extends ScalaHintsSettingsHolder {
  import hintsSettings._

  private var collectedHintTemplates = Seq.empty[(Seq[AlignedHintTemplate], ScExpression)]

  def collectMethodChainHints(editor: Editor, root: PsiElement): Unit =
    collectedHintTemplates =
      if (editor.isOneLineMode || !showMethodChainInlayHints) Seq.empty
      else gatherMethodChainHints(editor, root)

  private def gatherMethodChainHints(editor: Editor, root: PsiElement): Seq[(Seq[AlignedHintTemplate], ScExpression)] = {
    val document = editor.getDocument
    val minChainCount = math.max(2, uniqueTypesToShowMethodChains)
    val builder = Seq.newBuilder[(Seq[AlignedHintTemplate], ScExpression)]

    def gatherFor(elem: PsiElement): Set[Int] = {
      var occupiedLines = Set.empty[Int]
      for (child <- elem.children)
        occupiedLines |= gatherFor(child)

      val isAlreadyOccupied = occupiedLines
      for {
        MethodChain(methodChain) <- Some(elem)
        if methodChain.length >= uniqueTypesToShowMethodChains

        methodsAtLineEnd = methodChain.filter(isFollowedByLineEnd)

        if methodsAtLineEnd.length >= minChainCount

        methodAndTypes = methodsAtLineEnd
          .map(m => m -> m.`type`())
          .takeWhile(_._2.isRight)
          .map { case (m, ty) => m -> ty.right.get.tryExtractDesignatorSingleton }

        withoutPackagesAndSingletons = dropPackagesAndSingletons(methodAndTypes)

        if withoutPackagesAndSingletons.length >= minChainCount

        filteredMethodAndTypes = filterMethodsForUnalignedMode(withoutPackagesAndSingletons, methodChain)

        if filteredMethodAndTypes.length >= minChainCount

        uniqueTypeCount = filteredMethodAndTypes.map { case (m, ty) => ty.presentableText(m) }.toSet.size
        if uniqueTypeCount >= uniqueTypesToShowMethodChains
      } {
        val finalSelection = if (alignMethodChainInlayHints) withoutPackagesAndSingletons else filteredMethodAndTypes
        val group = for ((expr, ty) <- finalSelection)
          yield new AlignedHintTemplate(textFor(expr, ty, editor)) {
            override def endOffset: Int = expr.endOffset
          }

        val all = if (alignMethodChainInlayHints) {
          val begin = document.getLineNumber(group.head.endOffset)
          val end = document.getLineNumber(group.last.endOffset)
          val grouped = group.groupBy(tmpl => tmpl.line(document)).mapValues(_.head)
          occupiedLines ++= begin to end
          for (curLine <- begin to end if !isAlreadyOccupied(curLine)) yield
            grouped.getOrElse(
              curLine,
              new AlignedHintTemplate(Text("  ") :: Nil) {
                override val endOffset: Int = document.getLineEndOffset(curLine)
              }
            )
        } else group

        builder += all -> finalSelection.last._1
      }

      occupiedLines
    }

    gatherFor(root)
    builder.result()
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

    assert(collectedHintTemplates.forall(_._1.nonEmpty))

    // don't show inlay behind the outer most expression when it has an error
    val hintTemplates = collectedHintTemplates
      .map { case (group, outerExpr) => removeLastIfHasTypeMismatch(group, outerExpr) }
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

  private def dropPackagesAndSingletons(methodChain: Seq[(ScExpression, ScType)]): Seq[(ScExpression, ScType)] = methodChain.dropWhile {
    case (_, ty) =>
      ty match {
        case designated: DesignatorOwner => designated.element.is[PsiPackage] || designated.isSingleton
        case _ => false
      }

  }

  private def filterMethodsForUnalignedMode(methodAndTypes: Seq[(ScExpression, ScType)],
                                            methodChain: Seq[ScExpression]): Seq[(ScExpression, ScType)] = {

    val (firstExpr, _) = methodAndTypes.head
    val withoutFirst =
      if (firstExpr == methodChain.head && isUnqualifiedReference(firstExpr)) methodAndTypes.tail
      else methodAndTypes

    val withoutLast =
      if (methodAndTypes.last._1 != methodChain.last) withoutFirst
      else withoutFirst.init

    val withoutObvious =
      if (showObviousType) withoutLast
      else withoutLast.filterNot(hasObviousReturnType)

    withoutObvious
  }

  private def generateInlineHints(hintTemplates: Seq[Seq[AlignedHintTemplate]], inlayModel: InlayModel): Unit =
    for (hints <- hintTemplates; hint <- hints) {
      inlayModel.addInlineElement(hint.endOffset, false, new TextPartsHintRenderer(hint.textParts, None))
    }

  private def generateAlignedHints(hintTemplates: Seq[Seq[AlignedHintTemplate]], document: Document, charWidth: Int, inlayModel: InlayModel): Unit =
    hintTemplates.foreach(new AlignedInlayGroup(_)(inlayModel, document, charWidth))

  private def generateUnalignedHints(hintTemplates: Seq[Seq[AlignedHintTemplate]], charWidth: Int, inlayModel: InlayModel): Unit =
    for (hints <- hintTemplates; hint <- hints) {
      val inlay = inlayModel.addAfterLineEndElement(
        hint.endOffset,
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

    Text(": ") +: textPartsOf(ty, presentationLength)
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
         *  something
         *   .func {         // <- don't add type here
         *   }.func {        // <- add type here (return type of `something.func{...}`)
         *   }.func { x =>   // <- don't add type here
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
    Expression(expr).hasStableType ||
      isTypeObvious("", ty.presentableText(expr), methodName(expr))
  }

  private def removeLastIfHasTypeMismatch(methodsWithTypes: Seq[AlignedHintTemplate],
                                          outermostExpr: ScExpression): Seq[AlignedHintTemplate] =
    if (hasTypeMismatch(outermostExpr)) methodsWithTypes.init
    else methodsWithTypes

  private def hasTypeMismatch(expr: ScExpression): Boolean = AnnotatorHints.in(expr).nonEmpty

  @tailrec
  private def isUnqualifiedReference(expr: ScExpression): Boolean = expr match {
    case ref: ScReferenceExpression => !ref.isQualified
    case ScParenthesisedExpr(inner) => isUnqualifiedReference(inner)
    case _ => false
  }
}