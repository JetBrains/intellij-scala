package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, ToggleAction}
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.{Getter, Setter}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings.{getInstance => ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.settings.ScalaProjectSettingsConfigurable

import java.util.function.Consumer

object ScalaTypeHintsConfigurable {

  import java.lang.{Boolean => JBoolean}

  private def forceHintsUpdateOnNextPass(): Unit = {
    ImplicitHints.updateInAllEditors()
  }

  sealed abstract class ToggleTypeAction(@Nls text: String,
                                         @Nls description: String,
                                         getter: Getter[JBoolean],
                                         setter: Setter[JBoolean]) extends ToggleAction(text, description, null) {

    override def isSelected(event: AnActionEvent): Boolean = getter.get()

    override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
      setter.set(value)
      forceHintsUpdateOnNextPass()
    }

    override def getActionUpdateThread = ActionUpdateThread.BGT
  }


  class ToggleMethodChainInlayHintsAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("method.chain.hints.action.text"),
    ScalaCodeInsightBundle.message("method.chain.hints.action.description"),
    ScalaCodeInsightSettings.showMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.showMethodChainInlayHintsSetter()
  )

  class ToggleRangeHintsForToAndUntilAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("range.hints.for.to.and.until.action.text"),
    ScalaCodeInsightBundle.message("show.range.hints.for.to.and.until"),
    ScalaCodeInsightSettings.showRangeHintsForToAndUntilGetter(),
    ScalaCodeInsightSettings.showRangeHintsForToAndUntilSetter()
  )

  class ToggleRangeExclusiveHintAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("show.exclusive.range.hint.action.text"),
    ScalaCodeInsightBundle.message("show.exclusive.range.hint"),
    ScalaCodeInsightSettings.showExclusiveRangeHintDefaultGetter(),
    ScalaCodeInsightSettings.showExclusiveRangeHintDefaultSetter()
  )

  class ToggleTypeHintsAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("type.hints.action.text"),
    ScalaCodeInsightBundle.message("type.hints.action.description"),
    () => {
      ScalaCodeInsightSettings.showMethodResultTypeGetter().get() &&
        ScalaCodeInsightSettings.showMemberVariableTypeGetter().get() &&
        ScalaCodeInsightSettings.showLocalVariableTypeGetter().get()
    },
    (b) => {
      ScalaCodeInsightSettings.showMethodResultTypeSetter().set(b)
      ScalaCodeInsightSettings.showMemberVariableSetter().set(b)
      ScalaCodeInsightSettings.showLocalVariableTypeSetter().set(b)
    },
  )

  /**
   * A no-op action to provide a tip.
   */
  class XRayModeTipAction extends AnAction {
    override def update(e: AnActionEvent): Unit = {
      e.getPresentation.setText(ScalaCodeInsightBundle.message("xray.mode.tip.context.menu", ScalaHintsSettings.xRayModeShortcut))
    }

    override def actionPerformed(e: AnActionEvent): Unit = (
      ShowSettingsUtil.getInstance.showSettingsDialog(
        e.getProject,
        classOf[ScalaProjectSettingsConfigurable],
        (_.selectXRayModeTab()): Consumer[ScalaProjectSettingsConfigurable])
      )
  }

  object XRayModeTipAction {
    final val Id = "Scala.XRayModeTip"
  }

  class ToggleParameterHintsAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("parameter.name.hints.action.text"),
    ScalaCodeInsightBundle.message("parameter.name.hints.action.description"),
    ScalaCodeInsightSettings.showParameterNamesGetter(),
    ScalaCodeInsightSettings.showParameterNamesSetter()
  )

  class ToggleMethodResultTypeAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("show.method.result.action.text"),
    ScalaCodeInsightBundle.message("show.method.result.action.description"),
    ScalaCodeInsightSettings.showMethodResultTypeGetter,
    ScalaCodeInsightSettings.showMethodResultTypeSetter,
  )

  class ToggleMemberVariableTypeAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("show.member.variable.action.text"),
    ScalaCodeInsightBundle.message("show.member.variable.action.description"),
    ScalaCodeInsightSettings.showMemberVariableTypeGetter,
    ScalaCodeInsightSettings.showMemberVariableSetter,
  )

  class ToggleLocalVariableTypeAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("show.local.variable.action.text"),
    ScalaCodeInsightBundle.message("show.local.variable.action.description"),
    ScalaCodeInsightSettings.showLocalVariableTypeGetter,
    ScalaCodeInsightSettings.showLocalVariableTypeSetter,
  )
}
