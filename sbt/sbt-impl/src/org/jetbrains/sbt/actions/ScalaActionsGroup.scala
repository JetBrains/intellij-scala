package org.jetbrains.sbt.actions

import com.intellij.openapi.actionSystem.{ActionUpdateThread, DefaultActionGroup}

class ScalaActionsGroup extends DefaultActionGroup {

  override def getActionUpdateThread = ActionUpdateThread.BGT
}