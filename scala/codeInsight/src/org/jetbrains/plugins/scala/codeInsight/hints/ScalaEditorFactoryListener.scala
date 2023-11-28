package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings.{getInstance => DaemonCodeAnalyzerSettings}
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable.{getInstance => EditorSettingsExternalizable}
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

import java.awt.event.{FocusAdapter, FocusEvent, InputEvent, KeyAdapter, KeyEvent, MouseEvent, MouseMotionAdapter, MouseWheelEvent, MouseWheelListener}
import java.awt.{Component, Toolkit}
import javax.swing.{JScrollPane, JViewport, Timer}

class ScalaEditorFactoryListener extends EditorFactoryListener {
  private final val DoublePressInterval = 500
  private final val DoublePressHoldDuration = 500
  private final val PressAndHoldDuration = 1000

  private val longDelay = new Timer(PressAndHoldDuration, _ => xRayMode = true)
  private val shortDelay = new Timer(DoublePressHoldDuration, _ => xRayMode = true)

  private val ArrowKeys = Set(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN)

  private val actionListener = new AnActionListener {
    override def beforeActionPerformed(action: AnAction, event: AnActionEvent): Unit = event.getInputEvent match {
      case e: KeyEvent if ArrowKeys.contains(e.getKeyCode) =>
        xRayMode = false
        longDelay.stop()
        shortDelay.stop()
      case _ =>
    }
  }

  locally {
    longDelay.setRepeats(false)
    shortDelay.setRepeats(false)
    ApplicationManager.getApplication.getMessageBus.connect().subscribe(AnActionListener.TOPIC, actionListener)
  }

  override def editorCreated(event: EditorFactoryEvent): Unit = {
    val file = event.getEditor.getVirtualFile
    if (file == null || file.getExtension != "scala" && file.getExtension != "sc") return

    val component = event.getEditor.getContentComponent
    component.addFocusListener(editorFocusListener)
    component.addKeyListener(editorKeyListener)
    component.addMouseMotionListener(editorMouseListerner)
    component.getParent match {
      case viewport: JViewport => viewport.getParent match {
        case scrollPane: JScrollPane => scrollPane.addMouseWheelListener(editorMouseWheelListener)
        case _ =>
      }
      case _ =>
    }
  }

  override def editorReleased(event: EditorFactoryEvent): Unit = {
    val component = event.getEditor.getContentComponent
    component.removeFocusListener(editorFocusListener)
    component.removeKeyListener(editorKeyListener)
    component.removeMouseMotionListener(editorMouseListerner)
    component.getParent match {
      case viewport: JViewport => viewport.getParent match {
        case scrollPane: JScrollPane => scrollPane.removeMouseWheelListener(editorMouseWheelListener)
        case _ =>
      }
      case _ =>
    }
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
      if (e.getKeyCode == ModifierKey) {
        if (keyPressEvent == null) {
          if (System.currentTimeMillis() - firstKeyPressTime < DoublePressInterval) {
            firstKeyPressTime = 0
            keyPressEvent = e
            longDelay.stop()
            if (ScalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD) {
              shortDelay.setInitialDelay(DoublePressHoldDuration)
              shortDelay.start()
            }
          } else {
            firstKeyPressTime = System.currentTimeMillis()
            mouseHasMoved = false
            keyPressEvent = e
            if (ScalaApplicationSettings.XRAY_PRESS_AND_HOLD) {
              longDelay.setInitialDelay(PressAndHoldDuration)
              longDelay.start()
            }
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

  private val editorMouseWheelListener = new MouseWheelListener {
    override def mouseWheelMoved(e: MouseWheelEvent): Unit = if (xRayMode) {
      val isModifierKeyDown = if (SystemInfo.isMac) e.isMetaDown else e.isControlDown
      if (isModifierKeyDown) {
        e.consume()
        val modifierKeyMask = if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        val event = new MouseWheelEvent(e.getSource.asInstanceOf[Component],
          e.getID, e.getWhen, e.getModifiersEx & ~modifierKeyMask,
          e.getX, e.getY, e.getXOnScreen, e.getYOnScreen, e.getClickCount, e.isPopupTrigger,
          e.getScrollType, e.getScrollAmount, e.getWheelRotation, e.getPreciseWheelRotation)
        Toolkit.getDefaultToolkit.getSystemEventQueue.postEvent(event)
      }
    }
  }

  private var onTime: Long = _

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

      onTime = System.currentTimeMillis()
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

      keyPressEvent.getSource match {
        case component: EditorComponentImpl =>
          ScalaActionUsagesCollector.logXRayMode(component.getEditor.getProject, System.currentTimeMillis() - onTime)
        case _ =>
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

    ActionToolbarImpl.updateAllToolbarsImmediately(true)
  }

  private var indentGuidesShownSetting: Boolean = _

  private var showImplicitHintsSetting: Boolean = _

  private var showMethodSeparatorsSetting: Boolean = _
}
