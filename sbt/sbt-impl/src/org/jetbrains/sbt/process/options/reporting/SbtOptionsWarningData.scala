package org.jetbrains.sbt.process.options.reporting

import com.intellij.build.events.BuildEventsNls
import com.intellij.build.issue.BuildIssueQuickFix

//noinspection ApiStatus,UnstableApiUsage
private[sbt] final case class SbtOptionsWarningData(
  @BuildEventsNls.Title
  title: String,
  @BuildEventsNls.Description
  details: String,
  quickFixes: Seq[BuildIssueQuickFix] = Seq.empty,
)
