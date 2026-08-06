package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Key

import java.awt.Point

trait PopupPosition {
  def showPopup(popup: JBPopup, editor: Editor): Unit
}

object PopupPosition {
  private class CustomPosition extends PopupPosition {
    var point: Point = _

    override def showPopup(popup: JBPopup, editor: Editor): Unit = {
      if (point == null) {
        point = customLocationKey.get(editor)
      }
      if (point != null) {
        popup.showInScreenCoordinates(editor.getComponent, point)
      }
    }
  }

  def best: PopupPosition = _.showInBestPositionFor(_)

  def at(point: Point): PopupPosition = (popup, editor) => popup.showInScreenCoordinates(editor.getComponent, point)

  def atCustomLocation: PopupPosition = new CustomPosition

  def withCustomPopupLocation(editor: Editor, point: Point)(action: => Unit): Unit = {
    customLocationKey.set(editor, point)
    try
      action
    finally
      customLocationKey.set(editor, null)
  }

  private val customLocationKey: Key[Point] = Key.create("popup.custom.location")
}
