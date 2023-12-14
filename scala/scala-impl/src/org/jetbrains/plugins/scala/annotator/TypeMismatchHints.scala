package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.actionSystem.{ActionGroup, AnAction, AnActionEvent}
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColorsScheme}
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.Format.{InnerParentheses, OuterParentheses, Plain}
import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Match, Mismatch}
import org.jetbrains.plugins.scala.annotator.hints.Hint.MenuProvider
import org.jetbrains.plugins.scala.annotator.hints.{Text, _}
import org.jetbrains.plugins.scala.caches.CachesUtil.fileModCount
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.{ScalaProjectSettings, ScalaProjectSettingsConfigurable, ShowSettingsUtilImplExt}

import scala.util.chaining._

object TypeMismatchHints {
  private val ElementsPreceededByWhitespace = TokenSet.create(
    ScalaTokenTypes.kELSE, ScalaTokenTypes.kMACRO, ScalaTokenTypes.kCATCH, ScalaTokenTypes.tRBRACE)

  private[annotator] def createFor(element: PsiElement, expectedType: ScType, actualType: ScType)(implicit scheme: EditorColorsScheme, context: TypePresentationContext): AnnotatorHints = {
    val format = element match {
      case Parent(infix: ScInfixExpr) if infix.isRightAssoc && infix.argsElement == element => OuterParentheses
      case _: ScInfixExpr | _: ScPostfixExpr | _: ScFunctionExpr | _: ScIf | _: ScMatch => InnerParentheses
      case _ => Plain
    }

    val prefix = format match {
      case InnerParentheses | OuterParentheses => Seq(Hint(Seq(Text("(")), element, suffix = false))
      case _ => Seq.empty
    }

    val parts = (Text(": ") +: partsOf(expectedType, actualType, tooltipFor(expectedType, actualType))).pipe { parts =>
      format match {
        case InnerParentheses => Text(")") +: parts
        case OuterParentheses => parts :+ Text(")")
        case _ => parts
      }
    }

    val margin = if (format == InnerParentheses) None else Hint.leftInsetLikeChar(' ')

    def isWhitespaceRequiredBeforeNextElement =
      element.nextElement
        .fold(Iterator.empty: Iterator[PsiElement])(_.withNextSiblings)
        .dropWhile(e => e.is[PsiWhiteSpace] && !e.textContains('\n'))
        .nextOption()
        .exists(e => ElementsPreceededByWhitespace.contains(e.getNode.getElementType))

    val offsetDelta =
      if (format == OuterParentheses) 0
      else element.nextElement
        .filter(_.is[PsiWhiteSpace])
        .map(e => 0.max(e.getText.takeWhile(_ != '\n').length - (if (isWhitespaceRequiredBeforeNextElement) 1 else 0)))
        .getOrElse(0)

    val hints = prefix :+ Hint(parts, element, margin = margin, suffix = true, relatesToPrecedingElement = true, offsetDelta = offsetDelta, menu = typeMismatchHintContextMenu)

    AnnotatorHints(hints, fileModCount(element.getContainingFile))
  }

  private def partsOf(expected: ScType, actual: ScType, message: String)(implicit scheme: EditorColorsScheme, context: TypePresentationContext): Seq[Text] = {
    def toText(diff: Tree[TypeDiff]): Text = diff match {
      case Node(diffs @_*) =>
        Text(foldedString,
          foldedAttributes(diff.exists(_.is[Mismatch])),
          expansion = Some(() => diffs.map(toText)))
      case Leaf(Match(text, tpe)) =>
        Text(text,
          tooltip = () => tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
      case Leaf(Mismatch(text, tpe)) =>
        Text(text,
          attributes = Some(scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)),
          tooltip = () =>tpe.map(_.canonicalText.replaceFirst("_root_.", "")),
          navigatable = tpe.flatMap(_.extractClass))
    }
    TypeDiff.forActual(expected, actual)
      .flattenTo(TypeDiff.lengthOf(nodeLength = foldedString.length), maxLength = ScalaCodeInsightSettings.getInstance.presentationLength)
      .map(toText)
      .map(_.copy(errorTooltip = Some(message)))
  }


  private val typeMismatchHintContextMenu: MenuProvider = MenuProvider(
    new ActionGroup() {
      override def getChildren(e: AnActionEvent): Array[AnAction] = Array(
        new AnAction(ScalaBundle.message("type.mismatch.hint.context.menu.configure")) {
          override def actionPerformed(e: AnActionEvent): Unit = {
            ShowSettingsUtilImplExt.showSettingsDialog(
              e.getProject,
              classOf[ScalaProjectSettingsConfigurable],
              ScalaBundle.message("scala.project.settings.form.show.type.mismatch.hints")
            )
          }
        }
      )
    }
  )

  @Nls
  private[annotator] def tooltipFor(expectedType: ScType, actualType: ScType)(implicit context: TypePresentationContext): String = {
    val (diff1, diff2) = TypeDiff.forBoth(expectedType, actualType)

    tooltipForDiffTrees(ScalaBundle.message("type.mismatch.dot"), diff1, diff2)
  }

  // TODO Use a dedicated pass when built-in "advanced" hint API will be available in IDEA, SCL-14502
  def refreshIn(project: Project): Unit = {
    if (project == null)
      return

    if (!ScalaProjectSettings.in(project).isTypeMismatchHints) {
      AnnotatorHints.clearIn(project)
    }

    Class.forName("org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints")
      .getDeclaredMethod("updateInAllEditors")
      .invoke(null)
  }
}
