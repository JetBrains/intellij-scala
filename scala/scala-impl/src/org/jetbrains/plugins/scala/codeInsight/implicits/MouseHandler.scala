package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.event.{KeyAdapter, KeyEvent, MouseEvent}
import java.awt.{Cursor, Point}

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.event._
import com.intellij.openapi.editor.{Editor, EditorFactory, Inlay}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt

class MouseHandler(project: Project,
                   startupManager: StartupManager,
                   editorFactory: EditorFactory) extends AbstractProjectComponent(project) {

  private var activeHyperlink = Option.empty[(Inlay, Text)]

  private val mousePressListener = new EditorMouseAdapter {
    override def mousePressed(e: EditorMouseEvent): Unit = {
      MouseHandler.mousePressLocation = e.getMouseEvent.getPoint
    }

    override def mouseClicked(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        if (e.getMouseEvent.getButton == MouseEvent.BUTTON1) {
          if (e.getMouseEvent.isControlDown) {
            activeHyperlink.foreach { case (_, text) =>
              e.consume()
              deactivateActiveHypelink(e.getEditor)
              CommandProcessor.getInstance.executeCommand(project,
                () => text.navigatable.filter(_.canNavigate).foreach(_.navigate(true)), null, null)
            }
          } else {
            expandableAt(e.getEditor, e.getMouseEvent.getPoint).foreach { case (inlay, text) =>
              inlay.getRenderer.asOptionOf[TextRenderer].foreach { renderer =>
                text.expansion.foreach(it => renderer.replace(text, it()))
                inlay.updateSize()
              }
            }
          }
        }
      }
    }
  }

  private val mouseMovedListener = new EditorMouseMotionAdapter {
    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        if (e.getMouseEvent.isControlDown) {
          hyperlinkAt(e.getEditor, e.getMouseEvent.getPoint) match {
            case Some((inlay, text)) if activeHyperlink.contains((inlay, text)) =>
            // the hyperlink is already activated
            case Some((inlay, text)) =>
              deactivateActiveHypelink(e.getEditor)
              activateHyperlink(e.getEditor, inlay, text)
            case None =>
              deactivateActiveHypelink(e.getEditor)
          }
        } else {
          deactivateActiveHypelink(e.getEditor)
        }
      }
    }
  }

  startupManager.registerPostStartupActivity(() => {
    val multicaster = editorFactory.getEventMulticaster
    multicaster.addEditorMouseListener(mousePressListener, project)
    multicaster.addEditorMouseMotionListener(mouseMovedListener, project)
  })

  private def activateHyperlink(editor: Editor, inlay: Inlay, text: Text): Unit = {
    text.hyperlink = true
    activeHyperlink = Some(inlay, text)
    inlay.repaint()
    UIUtil.setCursor(editor.getContentComponent, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    editor.getContentComponent.addKeyListener(new KeyAdapter {
      override def keyPressed(keyEvent: KeyEvent): Unit = handle()

      override def keyReleased(keyEvent: KeyEvent): Unit = handle()

      private def handle(): Unit = {
        editor.getContentComponent.removeKeyListener(this)
        deactivateActiveHypelink(editor)
      }
    })
  }

  private def deactivateActiveHypelink(editor: Editor): Unit = {
    activeHyperlink.foreach { case (inlay, text) =>
      text.hyperlink = false
      inlay.repaint()
      editor.getContentComponent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
    }
    activeHyperlink = None
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
        val inlayPoint = {
          val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
          editor.visualPositionToXY(editor.offsetToVisualPosition(offset))
        }
        renderer.textAt(editor, point.x - inlayPoint.x).map((inlay, _))
      }
    }
}

object MouseHandler {
  var mousePressLocation: Point = new Point(0, 0)
}
