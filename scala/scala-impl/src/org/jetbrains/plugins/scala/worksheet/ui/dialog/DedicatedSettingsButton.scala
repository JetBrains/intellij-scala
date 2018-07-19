package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionToolbar, AnAction}

/**
  * User: Dmitry.Naydanov
  * Date: 17.07.18.
  */
abstract class DedicatedSettingsButton(name: String)  extends AnAction("", name, AllIcons.General.Settings)  {
  def getActionButton: ActionButton =
    new ActionButton(this, getTemplatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
}
