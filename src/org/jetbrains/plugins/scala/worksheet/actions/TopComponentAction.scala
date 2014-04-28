package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{ActionToolbar, ActionPlaces, AnAction}
import com.intellij.openapi.actionSystem.impl.ActionButton
import javax.swing.{JPanel, Icon}
import com.intellij.openapi.keymap.{KeymapUtil, KeymapManager}

/**
 * User: Dmitry Naydanov
 * Date: 2/17/14
 */
trait TopComponentAction {
  this: AnAction =>
  
  def shortcutId: Option[String] = None
  
  def genericText = ScalaBundle message bundleKey
  
  def bundleKey: String 
  
  def actionIcon: Icon
  
  def getActionButton = {
    val button = new ActionButton(this, getTemplatePresentation, ActionPlaces.EDITOR_TOOLBAR,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    button setToolTipText genericText
    button
  }
  
  def init(panel: JPanel) {
    val presentation = getTemplatePresentation
    
    val text = shortcutId flatMap {
      case id =>
        KeymapManager.getInstance.getActiveKeymap.getShortcuts(id).headOption map {
          case shortcut => 
            genericText + (" (" + KeymapUtil.getShortcutText(shortcut) + ")")  
        }
    } getOrElse genericText

    presentation setText text
    presentation setIcon actionIcon
    
    panel add getActionButton
  }
}
