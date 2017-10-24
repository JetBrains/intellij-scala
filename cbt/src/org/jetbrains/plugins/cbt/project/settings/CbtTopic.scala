package org.jetbrains.plugins.cbt.project.settings

import com.intellij.util.messages.Topic

object CbtTopic
  extends Topic[CbtProjectSettingsListener]("CBT-specific settings", classOf[CbtProjectSettingsListener])