package org.jetbrains.sbt
package project.settings

import com.intellij.util.messages.Topic

object SbtTopic extends Topic[SbtProjectSettingsListener]("sbt-specific settings", classOf[SbtProjectSettingsListener])