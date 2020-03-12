package org.jetbrains.plugins.scala.codeInsight.hints
package methodChains

import java.awt.Insets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme, EditorFontType}
import com.intellij.openapi.util.Disposer
import com.intellij.psi.{PsiElement, PsiFile, PsiPackage}
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.annotator.hints.{AnnotatorHints, Text}
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.ScalaMethodChainInlayHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.annotations.Expression
import org.jetbrains.plugins.scala.extensions.PsiFileExt

import scala.annotation.tailrec
import scala.collection.JavaConverters._

private[codeInsight] trait ScalaMethodChainInlayHintsPass {
  private var collectedHintTemplates = Seq.empty[(Seq[AlignedHintTemplate], ScExpression)]

  protected def settings: ScalaHintsSettings

  def collectMethodChainHints(editor: Editor, root: PsiFile): Unit =
    collectedHintTemplates =
      if (editor.isOneLineMode ||
        !settings.showMethodChainInlayHints ||
        root.isScala3File && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(root)) {
        Seq.empty
      } else {
        gatherMethodChainHints(editor, root)
      }

  private def gatherMethodChainHints(editor: Editor, root: PsiElement): Seq[(Seq[AlignedHintTemplate], ScExpression)] = {
    val document = editor.getDocument
    val minChainCount = math.max(2, settings.uniqueTypesToShowMethodChains)
    val builder = Seq.newBuilder[(Seq[AlignedHintTemplate], ScExpression)]

    def gatherFor(elem: PsiElement): Set[Int] = {
      var occupiedLines = Set.empty[Int]
      for (child <- elem.children)
        occupiedLines |= gatherFor(child)

      val isAlreadyOccupied = occupiedLines
      for {
        MethodChain(methodChain) <- Some(elem)
        if methodChain.length >= settings.uniqueTypesToShowMethodChains

        methodsAtLineEnd = methodChain.filter(isFollowedByLineEnd(_, alsoAfterLambdaArg = settings.alignMethodChainInlayHints))

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
        if uniqueTypeCount >= settings.uniqueTypesToShowMethodChains
      } {
        val finalSelection = if (settings.alignMethodChainInlayHints) withoutPackagesAndSingletons else filteredMethodAndTypes
        val group = for ((expr, ty) <- finalSelection)
          yield new AlignedHintTemplate(textFor(expr, ty, editor)) {
            override def endOffset: Int = expr.endOffset
          }

        val all = if (settings.alignMethodChainInlayHints) {
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
    } else if (settings.alignMethodChainInlayHints) {
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
      if (settings.showObviousType) withoutLast
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

    Text(": ") +: textPartsOf(ty, settings.presentationLength)
  }
}

private object ScalaMethodChainInlayHintsPass {
  private object CallerAndCall {
    def unapply(expr: ScExpression): Option[(ScExpression, MethodInvocation)] = expr match {
      case Parent((ref: ScReferenceExpression) && Parent(mc: ScMethodCall)) => Some(ref -> mc)
      case Parent(call@ScInfixExpr(`expr`, _, _)) =>  Some(expr -> call)
      case _ => None
    }
  }

  private def isFollowedByLineEnd(elem: PsiElement, alsoAfterLambdaArg: Boolean): Boolean = {
    elem match {
      case elem if elem.followedByNewLine(ignoreComments = false) =>
        true

      case CallerAndCall(ref, mc) =>
        /*
         * Check if we have a situation like
         *  something
         *   .func {             // <- don't add type here
         *   }.func {            // <- add type here (return type of `something.func{...}`)
         *   }.func { x =>       // <- add type here iff alsoAfterLambdaArg
         *   }.func { case x =>  // <- add type here iff alsoAfterLambdaArg
         *   }
         *  or
         *  something
         *   func {             // <- don't add type here
         *   } func {            // <- add type here (return type of `something.func{...}`)
         *   } func { x =>       // <- add type here iff alsoAfterLambdaArg
         *   } func { case x =>  // <- add type here iff alsoAfterLambdaArg
         *   }
         */

        // check for: x => \n
        def checkLambdaStart(mayBeArg: ScExpression): Boolean =
          mayBeArg.asOptionOf[ScFunctionExpr].exists { fExpr =>
            val params = fExpr.params
            !params.textContains('\n') &&
              !params.followedByNewLine(ignoreComments = false) &&
              params.getNextSiblingNotWhitespaceComment.nullSafe
                .exists(arrow => isArrowToken(arrow) && arrow.followedByNewLine(ignoreComments = false))
          }

        // check }.func( x => \n
        def checkLambdaInParen: Boolean =
          mc.argumentExpressions.headOption.exists(checkLambdaStart)

        // check }.func { x => \n
        def checkLamdaInBlock(blk: ScBlockExpr): Boolean =
          blk.asSimpleExpression.exists(checkLambdaStart)

        // check }.func { case x if expr => \n
        def checkClauseInBlock(blk: ScBlockExpr): Boolean =
          blk.caseClauses.flatMap(_.caseClause.toOption).exists(
            clause => clause.children
              .dropWhile(e => !e.textContains('\n') && !isArrowToken(e))
              .headOption
              .filter(isArrowToken)
              .exists(_.followedByNewLine(ignoreComments = false))
          )

        def argBegin = mc.argsElement match {
          case argList: ScArgumentExprList => argList.getFirstChild
          case parenthesis: ScParenthesisedExpr => parenthesis.getFirstChild
          case e => e
        }

        def isArgumentBegin = argBegin match {
          case blk: ScBlockExpr =>
            // check }.func {
            blk.getLBrace.exists(_.followedByNewLine(ignoreComments = false)) ||
              // check }.func { param =>
              // check }.func { case param =>
              (alsoAfterLambdaArg &&
                (checkLamdaInBlock(blk) || checkClauseInBlock(blk))
              )
          case firstElem if firstElem.elementType == ScalaTokenTypes.tLPARENTHESIS =>
            // check }.func(
            firstElem.followedByNewLine(ignoreComments = false) ||
              // check }.func(param =>
              (alsoAfterLambdaArg && checkLambdaInParen)
          case _ =>
            false
        }

        ref.followedByNewLine(ignoreComments = false) || isArgumentBegin
      case _ =>
        false
    }
  }

  private def isArrowToken(elem: PsiElement): Boolean = {
    val tt = elem.elementType
    tt == ScalaTokenTypes.tFUNTYPE || tt == ScalaTokenTypes.tFUNTYPE_ASCII
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