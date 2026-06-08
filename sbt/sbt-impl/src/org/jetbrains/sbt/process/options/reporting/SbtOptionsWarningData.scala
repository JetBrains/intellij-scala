package org.jetbrains.sbt.process.options.reporting

import com.intellij.build.events.BuildEventsNls

//noinspection ApiStatus,UnstableApiUsage
private[sbt] final case class SbtOptionsWarningData(
  @BuildEventsNls.Title
  title: String,
  @BuildEventsNls.Description
  details: String,
)
