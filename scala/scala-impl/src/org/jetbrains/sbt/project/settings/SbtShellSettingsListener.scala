package org.jetbrains.sbt.project.settings

import java.util.EventListener

import com.intellij.util.messages.Topic

trait SbtShellSettingsListener extends EventListener {
  def buildWithSbtShellSettingChanged(newValue: Boolean): Unit
}

object SbtShellSettingsListener {
  val topic: Topic[SbtShellSettingsListener] =
    Topic.create[SbtShellSettingsListener]("", classOf[SbtShellSettingsListener])
}
