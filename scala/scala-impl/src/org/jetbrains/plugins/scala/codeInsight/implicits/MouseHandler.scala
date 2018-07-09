package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.event._
import java.awt.{Cursor, Point}

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.event._
import com.intellij.openapi.editor.{Editor, EditorFactory, Inlay}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.{Key, SystemInfo}
import com.intellij.ui.{AncestorListenerAdapter, LightweightHint}
import com.intellij.util.ui.{JBUI, UIUtil}
import javax.swing.event.AncestorEvent
import org.jetbrains.plugins.scala.codeInsight.implicits.MouseHandler.EscKeyListenerKey
import org.jetbrains.plugins.scala.extensions.ObjectExt

class MouseHandler(project: Project,
                   startupManager: StartupManager,
                   editorFactory: EditorFactory) extends AbstractProjectComponent(project) {

  private var activeHyperlink = Option.empty[(Inlay, Text)]
  private var hyperlinkTooltip = Option.empty[LightweightHint]

  private var errorTooltip = Option.empty[LightweightHint]

  private var highlightedText = Set.empty[Text]
  private var highlightedInlay = Option.empty[Inlay]

  private val mousePressListener = new EditorMouseAdapter {
    override def mousePressed(e: EditorMouseEvent): Unit = {
      if (ImplicitHints.enabled) {
        MouseHandler.mousePressLocation = e.getMouseEvent.getPoint
      }
    }

    override def mouseClicked(e: EditorMouseEvent): Unit = {
      if (ImplicitHints.enabled && !e.isConsumed && project.isInitialized && !project.isDisposed) {
        if (e.getMouseEvent.getButton == MouseEvent.BUTTON1) {
          if (SystemInfo.isMac && e.getMouseEvent.isMetaDown || e.getMouseEvent.isControlDown) {
            activeHyperlink.foreach { case (_, text) =>
              e.consume()
              deactivateActiveHyperlink(e.getEditor)
              navigateTo(text)
            }
          } else {
            expandableAt(e.getEditor, e.getMouseEvent.getPoint).foreach { case (inlay, text) =>
              inlay.getRenderer.asOptionOf[TextRenderer].foreach { renderer =>
                renderer.expand(text)
                inlay.updateSize()
                if (!ImplicitHints.expanded) {
                  addEscKeyListenerTo(e.getEditor)
                }
              }
            }
          }
        } else if (e.getMouseEvent.getButton == MouseEvent.BUTTON3) {
          hyperlinkAt(e.getEditor, e.getMouseEvent.getPoint).foreach { case (_, text) =>
            e.consume()
            navigateTo(text)
          }
        }
      }
    }
  }

  private val mouseMovedListener = new EditorMouseMotionAdapter {
    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (ImplicitHints.enabled && !e.isConsumed && project.isInitialized && !project.isDisposed) {
        if (SystemInfo.isMac && e.getMouseEvent.isMetaDown || e.getMouseEvent.isControlDown) {
          hyperlinkAt(e.getEditor, e.getMouseEvent.getPoint) match {
            case Some((inlay, text)) =>
              if (!activeHyperlink.contains((inlay, text))) {
                deactivateActiveHyperlink (e.getEditor)
                activateHyperlink (e.getEditor, inlay, text, e.getMouseEvent)
              }
            case None =>
              deactivateActiveHyperlink(e.getEditor)
          }
        } else {
          textAt(e.getEditor, e.getMouseEvent.getPoint) match {
            case Some((inlay, text)) =>
              if (text.error && !errorTooltip.exists(_.isVisible)) {
                errorTooltip = text.tooltip.map(showTooltip(e.getEditor, e.getMouseEvent, _))
                errorTooltip.foreach(_.addHintListener(_ => errorTooltip = None))
              }
              highlightMatchedPairs(e.getEditor, inlay, text)
            case None =>
              clearExistingHighlighting()
          }
          deactivateActiveHyperlink(e.getEditor)
        }
      }
    }
  }

  startupManager.registerPostStartupActivity(() => {
    val multicaster = editorFactory.getEventMulticaster
    multicaster.addEditorMouseListener(mousePressListener, project)
    multicaster.addEditorMouseMotionListener(mouseMovedListener, project)
  })

  private def activateHyperlink(editor: Editor, inlay: Inlay, text: Text, event: MouseEvent): Unit = {
    text.hyperlink = true
    activeHyperlink = Some(inlay, text)
    inlay.repaint()
    UIUtil.setCursor(editor.getContentComponent, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    if (!hyperlinkTooltip.exists(_.isVisible)) {
      hyperlinkTooltip = text.tooltip.map(showTooltip(editor, event, _))
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

    hyperlinkTooltip.foreach(_.hide())
    hyperlinkTooltip = None
  }

  private def navigateTo(text: Text): Unit = {
    CommandProcessor.getInstance.executeCommand(project,
      () => text.navigatable.filter(_.canNavigate).foreach(_.navigate(true)), null, null)
  }

  private def expandableAt(editor: Editor, point: Point): Option[(Inlay, Text)] = textAt(editor, point).filter {
    case (_, text) => text.expansion.isDefined
  }

  private def hyperlinkAt(editor: Editor, point: Point): Option[(Inlay, Text)] = textAt(editor, point).filter {
    case (_, text) => text.navigatable.isDefined
  }

  private def textAt(editor: Editor, point: Point): Option[(Inlay, Text)] =
    Option(editor.getInlayModel.getElementAt(point)).flatMap { inlay =>
      inlay.getRenderer.asOptionOf[TextRenderer].flatMap { renderer =>
        val inlayPoint = editor.visualPositionToXY(inlay.getVisualPosition)
        renderer.textAt(editor, point.x - inlayPoint.x).map((inlay, _))
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

  private def showTooltip(editor: Editor, e: MouseEvent, text: String): LightweightHint = {
    val hint = {
      val label = HintUtil.createInformationLabel(text)
      label.setBorder(JBUI.Borders.empty(6, 6, 5, 6))
      new LightweightHint(label)
    }

    val constraint = HintManager.ABOVE

    val point = {
      val p = HintManagerImpl.getHintPosition(hint, editor,
        editor.xyToVisualPosition(e.getPoint), constraint)
      p.x = e.getXOnScreen - editor.getContentComponent.getTopLevelAncestor.getLocationOnScreen.x
      p
    }

    val manager = HintManagerImpl.getInstanceImpl

    manager.showEditorHint(hint, editor, point,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false,
      HintManagerImpl.createHintHint(editor, point, hint, constraint).setContentActive(false))

    hint
  }

  private def highlightMatchedPairs(editor: Editor, inlay: Inlay, text: Text): Unit = {
    inlay.getRenderer.asOptionOf[TextRenderer].flatMap(_.pairFor(text)) match {
      case Some(pair) =>
        if (highlightedText != Set(text, pair)) {
          clearExistingHighlighting()

          text.highlighted = true
          pair.highlighted = true

          inlay.repaint()

          highlightedText = Set(text, pair)
          highlightedInlay = Some(inlay)

          editor.getContentComponent.addKeyListener(new KeyAdapter {
            override def keyPressed(keyEvent: KeyEvent): Unit = handle()

            override def keyReleased(keyEvent: KeyEvent): Unit = handle()

            private def handle() = {
              editor.getContentComponent.removeKeyListener(this)
              clearExistingHighlighting()
            }
          })
        }
      case _ =>
        clearExistingHighlighting()
    }
  }

  private def clearExistingHighlighting(): Unit = {
    highlightedText.foreach(_.highlighted = false)
    highlightedInlay.foreach(_.repaint())

    highlightedText = Set.empty
    highlightedInlay = None
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
