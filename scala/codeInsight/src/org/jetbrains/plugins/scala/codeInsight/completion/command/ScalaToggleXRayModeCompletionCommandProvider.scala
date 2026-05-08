package org.jetbrains.plugins.scala.codeInsight.completion.command

import com.intellij.codeInsight.completion.command.commands.ActionCommandProvider
import com.intellij.icons.AllIcons
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.hints.XRayModeAction

//noinspection UnstableApiUsage
final class ScalaToggleXRayModeCompletionCommandProvider extends ActionCommandProvider(
  XRayModeAction.ActionId,
  ScalaCodeInsightBundle.message("xray.mode.command.completion.presentable.name"),
  AllIcons.Actions.Show,
  -1000,
  null, // TODO(SCL-24924): add some preview text
  java.util.List.of("Toggle X-Ray Mode"),
)
