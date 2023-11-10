package org.jetbrains.plugins.scala.codeInsight.hints.rangeHints

import com.intellij.codeInsight.hints.presentation.{PresentationFactory, RecursivelyUpdatingRootPresentation}
import com.intellij.codeInsight.hints.{HorizontalConstrainedPresentation, HorizontalConstraints}
import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, AnAction, AnActionEvent, Separator}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.{Editor, EditorCustomElementRenderer, InlayModel}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Disposer, Key}
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.hints.{ScalaHintsSettings, ScalaTypeHintsConfigurable}
import org.jetbrains.plugins.scala.codeInsight.hints.rangeHints.RangeInlayHintsPass._
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHints, TextPartsHintRenderer}
import org.jetbrains.plugins.scala.codeInsight.{ScalaCodeInsightBundle, ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import scala.jdk.CollectionConverters.IteratorHasAsScala

trait RangeInlayHintsPass {
  private var collectedHints = Seq.empty[HintTemplate]

  protected def settings: ScalaHintsSettings
  protected def isPreview: Boolean = false

  def collectRangeHints(editor: Editor, root: PsiFile): Unit =
    collectedHints =
      if (editor.isOneLineMode ||
        (!settings.showRangeHintsForToAndUntil && !settings.showExclusiveRangeHint) ||
        root.isScala3File && ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(root)) {
        Seq.empty
      } else {
        gatherRangeHints(editor, root)
      }

  private def gatherRangeHints(editor: Editor, root: PsiFile): Seq[HintTemplate] =
    root.depthFirst().flatMap { element =>
      element match {
        case ScInfixExpr(left, ref, right@InfixArgs(args)) if settings.showRangeHintsForToAndUntil =>
          val upperOffset = args match {
            case Seq(first, _) => first.startOffset
            case _ => right.startOffset
          }
          hintsFor(editor, left.endOffset, upperOffset, ref, args)
        case ScMethodCall(ref@ScReferenceExpression.withQualifier(qual), args)
          if settings.showRangeHintsForToAndUntil =>
          hintsFor(editor, qual.endOffset, args.headOption.fold(ref.endOffset)(_.startOffset), ref, args)
        case call@ScMethodCall(ref, args) if settings.showExclusiveRangeHint && isRangeApply(ref, call.target, args) =>
          Seq(HintTemplate(ref.endOffset, new TextPartsHintRenderer(Seq(Text(".exclusive")), rangeForExclusiveRange)))
        case _ =>
          Seq.empty
      }
    }.toSeq


  private def isRangeApply(ref: PsiElement, rr: Option[ScalaResolveResult], args: Seq[PsiElement]): Boolean = {
    if (isPreview) ref.textMatches("Range")
    else rr.exists(rr => args.size >= 2 && rr.element.asOptionOf[ScFunction].exists(_.qualifiedNameOpt.contains("scala.collection.immutable.Range.apply")))
  }


  private def hintsFor(editor: Editor, leftOffset: Int, rightOffset: Int, ref: ScReferenceExpression, args: Seq[PsiElement]): Seq[HintTemplate] = {
    sealed trait Direction
    object Direction {
      case object Up extends Direction
      case object Down extends Direction
    }

    val (name, isOk) = ref match {
      case ResolvesTo(target: PsiNamedElement) =>

        val targetClass = target
          .containingClassOfNameContext
          .flatMap(_.qualifiedNameOpt)
          .getOrElse("")

        (target.name, targetClass == "scala.runtime.RichInt" || targetClass == "scala.runtime.IntegralProxy")
      case _ if isPreview =>
        val name = ref.getText
        (name, rangeFunctions.contains(name))
      case _ =>
        return Seq.empty
    }

    def direction: Option[Direction] =
      args.lift(1) match {
        case Some(_: ScLiteral.Numeric) => Some(Direction.Up)
        case Some(ScPrefixExpr(ref, _: ScLiteral.Numeric)) if ref.textMatches("-") => Some(Direction.Down)
        case None => Some(Direction.Up)
        case _ => None
      }

    if (isOk) {
      rangeFunctions
        .get(name)
        .zip(direction)
        .fold(Seq.empty[HintTemplate]) { case ((up, down), direction) =>
          val (left, right) = direction match {
            case Direction.Up => ("≤", up)
            case Direction.Down => ("≥", down)
          }
          val factory = new PresentationFactory(editor.asInstanceOf[EditorImpl])
          Seq(
            HintTemplate(leftOffset, smallRounded(factory, left, relatesToPreceding = true)),
            HintTemplate(rightOffset, smallRounded(factory, right, relatesToPreceding = false))
          )
        }
    } else Seq.empty
  }

  def regenerateRangeInlayHints(editor: Editor, inlayModel: InlayModel, rootElement: PsiElement): Unit = {
    inlayModel
      .getInlineElementsInRange(rootElement.startOffset, rootElement.endOffset)
      .iterator()
      .asScala
      .filter(ScalaRangeInlayHintKey.isIn)
      .foreach(Disposer.dispose)

    collectedHints.foreach { tmpl =>
      inlayModel
        .addInlineElement(tmpl.offset, tmpl.renderer)
        .putUserData(ScalaRangeInlayHintKey, true)
    }
  }
}

object RangeInlayHintsPass {
  private case class HintTemplate(offset: Int, renderer: EditorCustomElementRenderer)
  private val ScalaRangeInlayHintKey: Key[Boolean] = Key.create[Boolean]("SCALA_RANGE_INLAY_HINT_KEY")

  private val rangeFunctions = Map(
    "until" -> ("<", ">"),
    "to" -> ("≤", "≥"),
  )

  private def smallRounded(factory: PresentationFactory, text: String, relatesToPreceding: Boolean): EditorCustomElementRenderer = {
    val presentation = factory.roundWithBackground(
      factory.text(text)
    )
    val rootPresentation = new HorizontalConstrainedPresentation(
      new RecursivelyUpdatingRootPresentation(presentation),
      new HorizontalConstraints(0, relatesToPreceding, false),
    )
    new InlineInlayRendererWithContextMenu(rootPresentation, rangeHintsForToAndUntilContextMenu)
  }


  private object InfixArgs {
    def unapply(e: PsiElement): Some[Seq[PsiElement]] = e match {
      case ScParenthesisedExpr(expr) => Some(Seq(expr))
      case ScTuple(args) => Some(args)
      case _ => Some(Seq(e))
    }
  }

  private val rangeHintsForToAndUntilContextMenu =
    createContextMenu(ScalaCodeInsightBundle.message("disable.range.hints.for.to.and.until"), _.showRangeHintsForToAndUntil = false, RangeHintsForToAndUntilSettingsModel.navigateTo)
  private val rangeForExclusiveRange =
    createContextMenu(ScalaCodeInsightBundle.message("disable.hints.for.exclusive.ranges"), _.showExclusiveRangeHint = false, ExclusiveRangeHintSettingsModel.navigateTo)

  private def createContextMenu(@Nls disableText: String, setSettings: ScalaCodeInsightSettings => Unit, navigate: Project => Unit): MenuProvider = {
    val actionGroup = new ActionGroup() {
      override def getChildren(e: AnActionEvent): Array[AnAction] = Array(
        new AnAction(disableText) {
          override def actionPerformed(e: AnActionEvent): Unit = {
            setSettings(ScalaCodeInsightSettings.getInstance())
            ImplicitHints.updateInAllEditors()
          }
        },
        new AnAction(ScalaCodeInsightBundle.message("configure.range.hints")) {
          override def actionPerformed(e: AnActionEvent): Unit = {
            navigate(e.getProject)
          }
        },
        Separator.getInstance,
        ActionManager.getInstance.getAction(ScalaTypeHintsConfigurable.XRayModeTipAction.Id)
      )
    }

    MenuProvider(actionGroup)
  }
}