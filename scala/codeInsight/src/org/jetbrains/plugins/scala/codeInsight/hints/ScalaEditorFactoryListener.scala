package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

import java.awt.event.{KeyAdapter, KeyEvent, MouseEvent, MouseMotionAdapter}
import javax.swing.Timer

class ScalaEditorFactoryListener extends EditorFactoryListener {
  private val longDelay = new Timer(1000, _ => xRayMode = true)
  private val shortDelay = new Timer(50, _ => xRayMode = true)

  locally {
    longDelay.setRepeats(false)
    shortDelay.setRepeats(false)
  }

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val component = event.getEditor.getContentComponent
    component.addKeyListener(editorKeyListener)
    component.addMouseMotionListener(editorMouseListerner)
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    val component = event.getEditor.getContentComponent
    component.removeKeyListener(editorKeyListener)
    component.removeMouseMotionListener(editorMouseListerner)
  }

  private var mouseHasMoved = false

  private val editorKeyListener = new KeyAdapter {
    private val ModifierKey = if (SystemInfo.isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL

    private var firstKeyPressTime = 0L

    override def keyPressed(e: KeyEvent): Unit = if (e.getKeyCode == ModifierKey) {
      if (System.currentTimeMillis() - firstKeyPressTime < 500) {
        firstKeyPressTime = 0
        longDelay.stop()
        shortDelay.start()
      } else {
        firstKeyPressTime = System.currentTimeMillis()
        mouseHasMoved = false
        longDelay.start()
      }
    } else {
      longDelay.stop()
      shortDelay.stop()
    }

    override def keyReleased(e: KeyEvent): Unit = {
      if (xRayMode) {
        xRayMode = false
      }
      longDelay.stop()
      shortDelay.stop()
    }
  }

  private val editorMouseListerner = new MouseMotionAdapter {
    override def mouseMoved(e: MouseEvent): Unit = {
      if (!mouseHasMoved) {
        longDelay.stop()
        mouseHasMoved = true
      }
    }
  }

  private def xRayMode: Boolean = ScalaHintsSettings.xRayMode

  private def xRayMode_=(b: Boolean): Unit = {
    ScalaHintsSettings.xRayMode = b
    ImplicitHints.enabled = b
    ImplicitHints.updateInAllEditors()
  }
}
