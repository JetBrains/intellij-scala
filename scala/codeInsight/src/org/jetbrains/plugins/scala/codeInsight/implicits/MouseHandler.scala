package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.event._
import java.awt.Cursor
import java.awt.Point

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.event._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.ui.UIUtil
import javax.swing.event.AncestorEvent
import javax.swing.SwingUtilities
import org.jetbrains.plugins.scala.annotator.hints.ErrorTooltip
import org.jetbrains.plugins.scala.annotator.hints.TooltipUI
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.implicits.MouseHandler.EscKeyListenerKey
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._

class MouseHandler extends ProjectManagerListener {

  private var activeHyperlink = Option.empty[(Inlay, Text)]
  private var highlightedMatches = Set.empty[(Inlay, Text)]

  private var hyperlinkTooltip = Option.empty[TooltipUI]
  private var errorTooltip = Option.empty[TooltipUI]

  private val mousePressListener = new EditorMouseListener {
    override def mousePressed(e: EditorMouseEvent): Unit = {
      if (handlingRequired(e)) {
        MouseHandler.mousePressLocation = e.getMouseEvent.getPoint
      }
    }

    override def mouseClicked(e: EditorMouseEvent): Unit = {
      if (handlingRequired(e) && !e.isConsumed) {
        val editor = e.getEditor
        val event = e.getMouseEvent
        errorTooltip.foreach(_.cancel())

        if (SwingUtilities.isLeftMouseButton(event)) {
          if (SystemInfo.isMac && event.isMetaDown || event.isControlDown) {
            hyperlinkAt(editor, event.getPoint).foreach { case TextAtPoint(text, _, _) =>
              e.consume()
              deactivateActiveHyperlink(editor)
              navigateTo(text, editor.getProject)
            }
          } else {
            expandableAt(editor, event.getPoint).foreach { case TextAtPoint(text, inlay, _) =>
              inlay.getRenderer.asOptionOf[TextPartsHintRenderer].foreach { renderer =>
                renderer.expand(text)
                inlay.update()
                if (!ImplicitHints.expanded) {
                  addEscKeyListenerTo(editor)
                }
              }
            }
          }
        } else if (SwingUtilities.isMiddleMouseButton(event) && activeHyperlink.isEmpty) {
          hyperlinkAt(editor, event.getPoint).foreach { case TextAtPoint(text, _, _) =>
            e.consume()
            navigateTo(text, editor.getProject)
          }
        } else {
          deactivateActiveHyperlink(editor)
        }

      }
    }
  }

  private val mouseMovedListener = new EditorMouseMotionListener {
    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (handlingRequired(e) && !e.isConsumed) {
        val textAtPoint = textAt(e.getEditor, e.getMouseEvent.getPoint)

        if (SystemInfo.isMac && e.getMouseEvent.isMetaDown || e.getMouseEvent.isControlDown) {
          textAtPoint match {
            case Some(TextAtPoint(text, inlay, _)) if text.navigatable.isDefined =>
              if (!activeHyperlink.contains((inlay, text))) {
                deactivateActiveHyperlink (e.getEditor)
                activateHyperlink(e.getEditor, inlay, text, e.getMouseEvent)
              }
            case _ =>
              deactivateActiveHyperlink(e.getEditor)
          }
          textAtPoint match {
            case Some(TextAtPoint(text, inlay, _)) =>
              highlightMatches(e.getEditor, inlay, text)
            case None =>
              clearHighlightedMatches()
          }
        } else {
          textAtPoint match {
            case Some(TextAtPoint(text, inlay, relativeX)) =>
              val sameUiShown = errorTooltip.exists(ui => text.errorTooltip.map(_.message).contains(ui.message) && !ui.isDisposed)
              if (text.errorTooltip.nonEmpty && !sameUiShown) {
                errorTooltip.foreach(_.cancel())
                errorTooltip = text.errorTooltip.map(showTooltip(e.getEditor, _, inlay, relativeX))
                errorTooltip.foreach(_.addHideListener(() => errorTooltip = None))
              }
            case None =>
              errorTooltip.foreach(_.cancel())
          }
          deactivateActiveHyperlink(e.getEditor)
        }

      }
    }
  }

  override def projectOpened(project: Project): Unit = {
    val multicaster = EditorFactory.getInstance().getEventMulticaster
    multicaster.addEditorMouseListener(mousePressListener, project.unloadAwareDisposable)
    multicaster.addEditorMouseMotionListener(mouseMovedListener, project.unloadAwareDisposable)
  }

  override def projectClosed(project: Project): Unit = {
    val multicaster = EditorFactory.getInstance().getEventMulticaster
    multicaster.removeEditorMouseListener(mousePressListener)
    multicaster.removeEditorMouseMotionListener(mouseMovedListener)

    activeHyperlink = None
    highlightedMatches = Set.empty
    hyperlinkTooltip = None
    errorTooltip = None
  }

  private def handlingRequired(e: EditorMouseEvent) = {
    val project = e.getEditor.getProject
    val isFromValidProject = project != null && project.isInitialized && !project.isDisposed

    def checkSettings = {
      val settings = ScalaProjectSettings.getInstance(project)
      ImplicitHints.enabled ||
        (settings.isTypeAwareHighlightingEnabled && (settings.isShowNotFoundImplicitArguments || settings.isShowAmbiguousImplicitArguments))
    }
    isFromValidProject && checkSettings
  }

  private def activateHyperlink(editor: Editor, inlay: Inlay, text: Text, event: MouseEvent): Unit = {
    text.hyperlink = true
    activeHyperlink = Some(inlay, text)
    inlay.repaint()
    UIUtil.setCursor(editor.getContentComponent, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    if (!hyperlinkTooltip.exists(_.isDisposed)) {
      hyperlinkTooltip = text.tooltip.map(showTooltip(editor, event, _, inlay))
    }

    editor.getContentComponent.addKeyListener(new KeyAdapter {
      override def keyPressed(keyEvent: KeyEvent): Unit = {
        // Why, in Windows, Control key press events are generated on mouse movement?
        if (keyEvent.getKeyCode != KeyEvent.VK_CONTROL) {
          handle()
        }
      }

      override def keyReleased(keyEvent: KeyEvent): Unit = handle()

      private def handle(): Unit = {
        editor.getContentComponent.removeKeyListener(this)
        deactivateActiveHyperlink(editor)
      }
    })
  }

  private def deactivateActiveHyperlink(editor: Editor): Unit = {
    activeHyperlink.foreach { case (inlay, text) =>
      text.hyperlink = false
      inlay.repaint()
      editor.getContentComponent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
    }
    activeHyperlink = None

    hyperlinkTooltip.foreach(_.cancel())
    hyperlinkTooltip = None
  }

  private def navigateTo(text: Text, project: Project): Unit = {
    CommandProcessor.getInstance.executeCommand(project,
      () => text.navigatable.filter(_.canNavigate).foreach(_.navigate(true)), null, null)
  }

  private def expandableAt(editor: Editor, point: Point): Option[TextAtPoint] = textAt(editor, point).filter {
    _.text.expansion.isDefined
  }

  private def hyperlinkAt(editor: Editor, point: Point): Option[TextAtPoint] = textAt(editor, point).filter {
    _.text.navigatable.isDefined
  }

  private def textAt(editor: Editor, point: Point): Option[TextAtPoint] =
    Option(editor.getInlayModel.getElementAt(point)).flatMap { inlay =>
      inlay.getRenderer.asOptionOf[TextPartsHintRenderer].flatMap { renderer =>
        val inlayPoint = editor.visualPositionToXY(inlay.getVisualPosition)
        val x = point.x - inlayPoint.x
        renderer.textAt(editor, x).map(t => TextAtPoint(t._1, inlay, t._2))
      }
    }

  private def addEscKeyListenerTo(editor: Editor): Unit = {
    if (editor.getUserData(EscKeyListenerKey) == null) {
      val keyListener = new KeyAdapter {
        override def keyTyped(keyEvent: KeyEvent): Unit = {
          if (keyEvent.getKeyChar == KeyEvent.VK_ESCAPE) {
            ImplicitHints.collapseIn(editor)
          }
        }
      }

      val component = editor.getContentComponent

      component.addKeyListener(keyListener)
      editor.putUserData(EscKeyListenerKey, keyListener)

      component.addAncestorListener(new AncestorListenerAdapter {
        override def ancestorRemoved(event: AncestorEvent): Unit = {
          component.removeKeyListener(keyListener)
          editor.putUserData(EscKeyListenerKey, null)

          component.removeAncestorListener(this)
        }
      })
    }
  }

  private def showTooltip(editor: Editor, errorTooltip: ErrorTooltip, inlay: Inlay, relativeX: Int): TooltipUI = {
    val offset = inlay.getOffset
    val x = editor.offsetToXY(offset).x + editor.getContentComponent.getLocationOnScreen.x + relativeX

    TooltipUI(errorTooltip, editor).show(editor, new Point(x, lineScreenY(editor, inlay)))
  }

  private def showTooltip(editor: Editor, e: MouseEvent, text: String, inlay: Inlay): TooltipUI = {
    TooltipUI(text, editor).show(editor, new Point(e.getXOnScreen, lineScreenY(editor, inlay)))
  }

  private def lineScreenY(editor: Editor, inlay: Inlay): Int = {
    val line = editor.offsetToLogicalPosition(inlay.getOffset).line
    editor.visualLineToY(line) + editor.getContentComponent.getLocationOnScreen.y
  }

  private def highlightMatches(editor: Editor, inlay: Inlay, text: Text): Unit = {
    findPairFor(editor, (inlay, text)) match {
      case Some((pairInlay, pairText)) =>
        val matches: Set[(Inlay, Text)] = Set((inlay, text), (pairInlay, pairText))

        if (highlightedMatches != matches) {
          clearHighlightedMatches()

          text.highlighted = true
          pairText.highlighted = true

          inlay.repaint()
          pairInlay.repaint()

          highlightedMatches = matches

          editor.getContentComponent.addKeyListener(new KeyAdapter {
            override def keyReleased(keyEvent: KeyEvent): Unit = {
              editor.getContentComponent.removeKeyListener(this)
              clearHighlightedMatches()
            }
          })
        }
      case _ =>
        clearHighlightedMatches()
    }
  }

  private def findPairFor(editor: Editor, element: (Inlay, Text)): Option[(Inlay, Text)] = {
    val (lineStart, lineEnd) = {
      val line = editor.getDocument.getLineNumber(element._1.getOffset)
      (editor.getDocument.getLineStartOffset(line), editor.getDocument.getLineEndOffset(line))
    }

    val elements = editor.getInlayModel.getInlineElementsInRange(lineStart, lineEnd).asScala.toSeq.flatMap { inlay =>
      inlay.getRenderer.asOptionOf[TextPartsHintRenderer].toSeq.flatMap(_.parts.map((inlay, _)))
    }

    pairFor[(Inlay, Text)](element, elements, _._2.string == "(", _._2.string == ")")
      .orElse(pairFor[(Inlay, Text)](element, elements, _._2.string == "[", _._2.string == "]"))
      .orElse(pairFor[(Inlay, Text)](element, elements, _._2.string == "{", _._2.string == "}"))
  }

  private def clearHighlightedMatches(): Unit = {
    highlightedMatches.foreach { case (inlay, text) =>
      text.highlighted = false
      inlay.repaint()
    }

    highlightedMatches = Set.empty
  }
}

private object MouseHandler {
  private val EscKeyListenerKey: Key[KeyListener] = Key.create[KeyListener]("SCALA_IMPLICIT_HINTS_KEY_LISTENER")

  var mousePressLocation: Point = new Point(0, 0)

  def removeEscKeyListeners(): Unit = {
    EditorFactory.getInstance.getAllEditors.foreach(removeEscKeyListenerFrom)
  }

  private def removeEscKeyListenerFrom(editor: Editor): Unit = {
    Option(editor.getUserData(MouseHandler.EscKeyListenerKey)).foreach { listener =>
      editor.getContentComponent.removeKeyListener(listener)
      editor.putUserData(MouseHandler.EscKeyListenerKey, null)
    }
  }
}
