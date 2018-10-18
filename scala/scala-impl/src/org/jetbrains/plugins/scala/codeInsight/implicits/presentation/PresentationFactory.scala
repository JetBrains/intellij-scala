package org.jetbrains.plugins.scala.codeInsight.implicits
package presentation

import java.awt.event.MouseEvent
import java.awt._

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{EditorColors, EditorFontType, TextAttributesKey}
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.{EffectType, TextAttributes}
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.{JBUI, UIUtil}
import PresentationFactory._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._

import scala.Function.const

class PresentationFactory(editor: EditorImpl) {
  private def ascent = editor.getAscent

  private def descent = editor.getDescent

  private def lineHeight = editor.getLineHeight

  private def charHeight = editor.getCharHeight

  private def fontMetrics(font: Font) = component.getFontMetrics(font)

  private def component = editor.getContentComponent

  private def font = editor.getColorsScheme.getFont(EditorFontType.PLAIN)

  val empty: Presentation = space(0)

  def space(width: Int): Presentation =
    new Space(width, editor.getLineHeight)

  def text(text: String, fontProvider: EditorFontType => Font): Presentation = {
    val width = fontMetrics(font).stringWidth(text)
    new Background(new Effect(new Text(width, lineHeight, text, ascent, fontProvider), font, lineHeight, ascent, descent))
  }

  def sequence(presentations: Presentation*): Presentation =
    new Sequence(lineHeight, presentations: _*)

  // withAttributes
  def attributes(transform: TextAttributes => TextAttributes, presentation: Presentation): Presentation =
    new Attributes(presentation, transform)

  def effects(font: Font, presentation: Presentation): Presentation =
    new Effect(presentation, font, lineHeight, ascent, descent)

  def background(color: Color, presentation: Presentation): Presentation =
    new Background(presentation)

  def rounding(arcWidth: Int, arcHeight: Int, presentation: Presentation): Presentation =
    new Rounding(presentation, arcWidth, arcHeight)

  def insets(left: Int, right: Int, presentation: Presentation): Presentation =
    new Sequence(lineHeight, new Space(left, lineHeight), presentation, new Space(right, lineHeight))

  def withFolding(folded: Presentation, expanded: => Presentation): Presentation =
    expansion(expanded, new Attributes(folded, _ + adjusted(attributesOf(EditorColors.FOLDED_TEXT_ATTRIBUTES))))

  private def attributesOf(key: TextAttributesKey) =
    Option(editor.getColorsScheme.getAttributes(key)).getOrElse(new TextAttributes())

  // Add custom colors for folding inside inlay hints (SCL-13996)?
  private def adjusted(attributes: TextAttributes): TextAttributes = {
    val result = attributes.clone()
    if (UIUtil.isUnderDarcula && result.getBackgroundColor != null) {
      result.setBackgroundColor(result.getBackgroundColor.brighter)
    }
    result
  }

  private def expansion(expanded: => Presentation, presentation: Presentation): Presentation = {
    val expansion = new Expansion(presentation, expanded)

    new OnClick(expansion, Button.Left, e => {
      if (!expansion.expanded) {
        expansion.expand(1)
        e.consume()
      }
    })
  }

  def withTooltip(tooltip: String, presentation: Presentation): Presentation = {
    if (tooltip.isEmpty) presentation
    else onHover(tooltipHandler(tooltip), presentation)
  }

  // TODO only withTooltip?
  private def tooltipHandler(tooltip: String): Option[MouseEvent] => Unit = {
    var hint: Option[LightweightHint] = None

    val handler: Option[MouseEvent] => Unit = {
      case Some(e) =>
        if (hint.forall(!_.isVisible)) {
          hint = Some(showTooltip(editor, e, tooltip))
        }
      case None =>
        hint.foreach(_.hide())
        hint = None
    }

    handler
  }

  def withNavigation(target: PsiElement, tooltip: Option[String], presentation: Presentation): Presentation =
    navigation(asHyperlink, tooltip.map(tooltipHandler).getOrElse(const(())), _ => navigateTo(target), presentation)

  private def asHyperlink(presentation: Presentation): Presentation = {
    val as = attributesOf(EditorColors.REFERENCE_HYPERLINK_COLOR).clone()
    as.setEffectType(EffectType.LINE_UNDERSCORE)
    attributes(_ + as, presentation)
  }

  private def navigateTo(e: PsiElement): Unit = {
    e.asOptionOf[Navigatable].filter(_.canNavigate).foreach { navigatable =>
      CommandProcessor.getInstance.executeCommand(e.getProject, navigatable.navigate(true), null, null)
    }
  }

  private def navigation(decorator: Presentation => Presentation, onHover: Option[MouseEvent] => Unit, onClick: MouseEvent => Unit, presentation: Presentation): Presentation = {
    val inner = new OnClick(presentation, Button.Middle, onClick)

    val forwarding = new DynamicForwarding(inner)

    new OnHover(forwarding, isControlDown, e => {
      val cursor = if (e.isDefined) Cursor.HAND_CURSOR else Cursor.TEXT_CURSOR
      component.setCursor(Cursor.getPredefinedCursor(cursor))

      forwarding.delegate = if (e.isDefined) new OnClick(decorator(presentation), Button.Left, onClick) else inner

      onHover(e)
    })
  }

  def synchronous(decorator: Presentation => Presentation, presentation1: Presentation, presentation2: Presentation): (Presentation, Presentation) = {
    val result = synchronous0(decorator, presentation1, presentation2)
    (result(0), result(1))
  }

  private def synchronous0(decorator: Presentation => Presentation, presentations: Presentation*): Seq[Presentation] = {
    val forwardings = presentations.map(new DynamicForwarding(_))

    forwardings.map(it => new OnHover(it, isControlDown, e => {
      forwardings.zip(presentations).foreach { case (forwarding, presentation) =>
        forwarding.delegate = if (e.isDefined) decorator(presentation) else presentation
      }
    }))
  }

  def onHover(handler: Option[MouseEvent] => Unit, presentation: Presentation): Presentation = {
    new OnHover(presentation, _ => true, handler)
  }

  def onClick(handler: MouseEvent => Unit, button: Button, presentation: Presentation): Presentation =
    new OnClick(presentation, button, handler)
}

private object PresentationFactory {
  private def isControlDown(e: MouseEvent): Boolean =
    SystemInfo.isMac && e.isMetaDown || e.isControlDown

  private def showTooltip(editor: Editor, e: MouseEvent, text: String): LightweightHint = {
    val hint = {
      val label = HintUtil.createInformationLabel(text)
      label.setBorder(JBUI.Borders.empty(6, 6, 5, 6))
      new LightweightHint(label)
    }

    val constraint = HintManager.ABOVE

    val point = {
      val pointOnEditor = locationAt(e, editor.getContentComponent)
      val p = HintManagerImpl.getHintPosition(hint, editor, editor.xyToVisualPosition(pointOnEditor), constraint)
      p.x = e.getXOnScreen - editor.getContentComponent.getTopLevelAncestor.getLocationOnScreen.x
      p
    }

    HintManagerImpl.getInstanceImpl.showEditorHint(hint, editor, point,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false,
      HintManagerImpl.createHintHint(editor, point, hint, constraint).setContentActive(false))

    hint
  }

  private def locationAt(e: MouseEvent, component: Component): Point = {
    val pointOnScreen = component.getLocationOnScreen
    new Point(e.getXOnScreen - pointOnScreen.x, e.getYOnScreen - pointOnScreen.y)
  }
}