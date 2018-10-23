package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.event._
import java.awt.{Component, Point}

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.event._
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.ui.AncestorListenerAdapter
import javax.swing.event.AncestorEvent
import org.jetbrains.plugins.scala.codeInsight.implicits.MouseHandler._

class MouseHandler(project: Project,
                   startupManager: StartupManager,
                   editorFactory: EditorFactory) extends ProjectComponent {

  private var activeInlay = Option.empty[Inlay]

  private val mouseListener = new EditorMouseListener {
    override def mouseClicked(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        val editor = e.getEditor
        val event = e.getMouseEvent

        inlayAt(editor, event.getPoint).foreach { inlay =>
          val inlayPoint = editor.visualPositionToXY(inlay.getVisualPosition)
          inlay.presentation.mouseClicked(shifted(event, -inlayPoint.x, -inlayPoint.y))

          registerKeyListenerFor(editor)
        }
      }
    }
  }

  private def inlayAt(editor: Editor, point: Point): Option[Inlay] =
    Option(editor.getInlayModel.getElementAt(point)).flatMap { inlay =>
      if (inlay.getRenderer.isInstanceOf[PresentationRenderer]) Some(inlay) else None
    }

  private def shifted(e: MouseEvent, dx: Int, dy: Int): MouseEvent =
    new MouseEvent(e.getSource.asInstanceOf[Component], e.getID, e.getWhen, e.getModifiers,
      e.getX + dx, e.getY + dy, e.getXOnScreen, e.getYOnScreen, e.getClickCount, e.isPopupTrigger, e.getButton)

  private val mouseMovedListener = new EditorMouseMotionAdapter {
    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        val editor = e.getEditor
        val event = e.getMouseEvent

        registerKeyListenerFor(editor)

        val inlay = inlayAt(editor, event.getPoint)

        if (activeInlay != inlay) {
          activeInlay.foreach(_.presentation.mouseExited())
          activeInlay = inlay
        }

        inlay.foreach { inlay =>
          val point = editor.visualPositionToXY(inlay.getVisualPosition)
          inlay.presentation.mouseMoved(shifted(event, -point.x, -point.y))
        }
      }
    }
  }

  override def projectOpened(): Unit = {
    val multicaster = editorFactory.getEventMulticaster
    multicaster.addEditorMouseListener(mouseListener, project)
    multicaster.addEditorMouseMotionListener(mouseMovedListener, project)
  }

  override def projectClosed(): Unit = {
    val multicaster = editorFactory.getEventMulticaster
    multicaster.removeEditorMouseListener(mouseListener)
    multicaster.removeEditorMouseMotionListener(mouseMovedListener)

    activeInlay = None
  }

  private def registerKeyListenerFor(editor: Editor): Unit = {
    if (editor.getUserData(EscKeyListenerKey) == null) {
      val keyListener: KeyAdapter = new KeyAdapter {
        override def keyTyped(keyEvent: KeyEvent): Unit = {
          if (keyEvent.getKeyChar == KeyEvent.VK_ESCAPE) {
            ImplicitHints.collapseIn(editor)
          }
        }

        override def keyPressed(keyEvent: KeyEvent): Unit = {
          // Why, in Windows, Control key press events are generated on mouse movement?
          if (keyEvent.getKeyCode != KeyEvent.VK_CONTROL) {
            handle()
          }
        }

        override def keyReleased(keyEvent: KeyEvent): Unit = handle()

        private def handle(): Unit = {
          activeInlay.foreach(_.presentation.mouseExited())
          activeInlay = None
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
}

private object MouseHandler {
  private val EscKeyListenerKey: Key[KeyListener] = Key.create[KeyListener]("SCALA_IMPLICIT_HINTS_KEY_LISTENER")

  // TODO
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
