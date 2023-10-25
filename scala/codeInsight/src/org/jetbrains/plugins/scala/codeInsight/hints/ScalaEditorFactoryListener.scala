package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, AnActionEvent}
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

import java.awt.event.{FocusAdapter, FocusEvent, KeyAdapter, KeyEvent, MouseEvent, MouseMotionAdapter}
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
    component.addFocusListener(editorFocusListener)
    component.addKeyListener(editorKeyListener)
    component.addMouseMotionListener(editorMouseListerner)
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    val component = event.getEditor.getContentComponent
    component.removeFocusListener(editorFocusListener)
    component.removeKeyListener(editorKeyListener)
    component.removeMouseMotionListener(editorMouseListerner)
  }

  private var keyPressEvent: KeyEvent = _

  private var mouseHasMoved = false

  private val editorFocusListener = new FocusAdapter {
    override def focusLost(e: FocusEvent): Unit = {
      xRayMode = false
      keyPressEvent = null
      longDelay.stop()
      shortDelay.stop()
    }
  }

  private val editorKeyListener = new KeyAdapter {
    private val ModifierKey = if (SystemInfo.isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL

    private var firstKeyPressTime = 0L

    override def keyPressed(e: KeyEvent): Unit = if (Registry.is("scala.xray.mode")) {
      if (e.getKeyCode == ModifierKey) {
        if (System.currentTimeMillis() - firstKeyPressTime < 500) {
          firstKeyPressTime = 0
          keyPressEvent = e
          longDelay.stop()
          shortDelay.start()
        } else {
          firstKeyPressTime = System.currentTimeMillis()
          mouseHasMoved = false
          //longDelay.start()
        }
      } else {
        longDelay.stop()
        shortDelay.stop()
      }
    }

    override def keyReleased(e: KeyEvent): Unit = {
      xRayMode = false
      keyPressEvent = null
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

  private def xRayMode_=(enabled: Boolean): Unit = {
    if (ScalaHintsSettings.xRayMode == enabled) return

    ScalaHintsSettings.xRayMode = enabled

    if (enabled) {
      indentGuidesShownSetting = EditorSettingsExternalizable.getInstance.isIndentGuidesShown
      EditorSettingsExternalizable.getInstance.setIndentGuidesShown(true)

      showImplicitHintsSetting = ImplicitHints.enabled
      ImplicitHints.enabled = true
      ImplicitHints.updateInAllEditors()

      keyPressEvent.getSource match {
        case component: EditorComponentImpl =>
          val action = ActionManager.getInstance.getAction(XRayModeAction.Id)
          val event = AnActionEvent.createFromInputEvent(keyPressEvent, ActionPlaces.KEYBOARD_SHORTCUT, null, component.getEditor.getDataContext)
          ActionUtil.performActionDumbAwareWithCallbacks(action, event)
        case _ =>
      }
    } else {
      EditorSettingsExternalizable.getInstance.setIndentGuidesShown(indentGuidesShownSetting)

      ImplicitHints.enabled = showImplicitHintsSetting
      ImplicitHints.updateInAllEditors()
    }
  }

  private var indentGuidesShownSetting: Boolean = _

  private var showImplicitHintsSetting: Boolean = _
}
