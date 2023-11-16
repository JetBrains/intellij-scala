package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings.{getInstance => DaemonCodeAnalyzerSettings}
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, AnActionEvent}
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable.{getInstance => EditorSettingsExternalizable}
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import java.awt.event.{FocusAdapter, FocusEvent, KeyAdapter, KeyEvent, MouseEvent, MouseMotionAdapter}
import javax.swing.Timer

class ScalaEditorFactoryListener extends EditorFactoryListener {
  private val longDelay = new Timer(ScalaApplicationSettings.XRAY_PRESS_AND_HOLD_DURATION, _ => xRayMode = true)
  private val shortDelay = new Timer(ScalaApplicationSettings.XRAY_DOUBLE_PRESS_HOLD_DURATION, _ => xRayMode = true)

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

    override def keyPressed(e: KeyEvent): Unit = if (ScalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD || ScalaApplicationSettings.XRAY_PRESS_AND_HOLD) {
      if (e.getKeyCode == ModifierKey && keyPressEvent == null) {
        if (System.currentTimeMillis() - firstKeyPressTime < ScalaApplicationSettings.XRAY_DOUBLE_PRESS_INTERVAL) {
          firstKeyPressTime = 0
          keyPressEvent = e
          longDelay.stop()
          if (ScalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD) {
            shortDelay.setInitialDelay(ScalaApplicationSettings.XRAY_DOUBLE_PRESS_HOLD_DURATION)
            shortDelay.start()
          }
        } else {
          firstKeyPressTime = System.currentTimeMillis()
          mouseHasMoved = false
          keyPressEvent = e
          if (ScalaApplicationSettings.XRAY_PRESS_AND_HOLD) {
            longDelay.setInitialDelay(ScalaApplicationSettings.XRAY_PRESS_AND_HOLD_DURATION)
            longDelay.start()
          }
        }
      } else {
        xRayMode = false
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
      if (ScalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES) {
        indentGuidesShownSetting = EditorSettingsExternalizable.isIndentGuidesShown
        EditorSettingsExternalizable.setIndentGuidesShown(true)
      }

      if (ScalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS) {
        showMethodSeparatorsSetting = DaemonCodeAnalyzerSettings.SHOW_METHOD_SEPARATORS
        DaemonCodeAnalyzerSettings.SHOW_METHOD_SEPARATORS = true
      }

      if (ScalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS) {
        showImplicitHintsSetting = ImplicitHints.enabled
        ImplicitHints.enabled = true
      }

      keyPressEvent.getSource match {
        case component: EditorComponentImpl =>
          val action = ActionManager.getInstance.getAction(XRayModeAction.Id)
          val event = AnActionEvent.createFromInputEvent(keyPressEvent, ActionPlaces.KEYBOARD_SHORTCUT, null, component.getEditor.getDataContext)
          ActionUtil.performActionDumbAwareWithCallbacks(action, event)
        case _ =>
      }
    } else {
      if (ScalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES) {
        EditorSettingsExternalizable.setIndentGuidesShown(indentGuidesShownSetting)
      }

      if (ScalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS) {
        DaemonCodeAnalyzerSettings.SHOW_METHOD_SEPARATORS = showMethodSeparatorsSetting
      }

      if (ScalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS) {
        ImplicitHints.enabled = showImplicitHintsSetting
      }
    }

    if (ScalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS || ScalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS) {
      keyPressEvent.getSource match {
        case component: EditorComponentImpl =>
          ParameterHintsPassFactory.forceHintsUpdateOnNextPass(component.getEditor)
        case _ =>
      }
    }

    ImplicitHints.updateInAllEditors()
  }

  private var indentGuidesShownSetting: Boolean = _

  private var showImplicitHintsSetting: Boolean = _

  private var showMethodSeparatorsSetting: Boolean = _
}
