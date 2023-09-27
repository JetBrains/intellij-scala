package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, ToggleAction}
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.{Getter, Setter}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings.{getInstance => ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints
import org.jetbrains.plugins.scala.extensions._

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
  }


  class ToggleMethodChainInlayHintsAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("method.chain.hints.action.text"),
    ScalaCodeInsightBundle.message("method.chain.hints.action.description"),
    ScalaCodeInsightSettings.showMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.showMethodChainInlayHintsSetter()
  )

  class ToggleRangeHintsForToAndUntilAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("range.hints.for.to.and.until"),
    ScalaCodeInsightBundle.message("show.range.hints.for.to.and.until"),
    ScalaCodeInsightSettings.showRangeHintsForToAndUntilGetter(),
    ScalaCodeInsightSettings.showRangeHintsForToAndUntilSetter()
  )

  class ToggleRangeExclusiveHintAction extends ToggleTypeAction(
    ScalaCodeInsightBundle.message("range.exclusive.hint"),
    ScalaCodeInsightBundle.message("show.exclusive.range.hint"),
    ScalaCodeInsightSettings.showExclusiveRangeHintDefaultGetter(),
    ScalaCodeInsightSettings.showExclusiveRangeHintDefaultSetter()
  )

  /*
  class ToggleMethodResultTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showMethodResultTypeGetter,
    ScalaCodeInsightSettings.showMethodResultTypeSetter
  )

  class ToggleMemberVariableTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showMemberVariableTypeGetter,
    ScalaCodeInsightSettings.showMemberVariableSetter
  )

  class ToggleLocalVariableTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showLocalVariableTypeGetter,
    ScalaCodeInsightSettings.showLocalVariableTypeSetter
  )
  class ToggleObviousTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showObviousTypeGetter,
    ScalaCodeInsightSettings.showObviousTypeSetter
  )

  class ToggleAlignMethodChainInlayHintsAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.alignMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.alignMethodChainInlayHintsSetter()
  )
  */
  class ConfigureTypeHintActions extends AnAction(
    ScalaCodeInsightBundle.message("configure.type.hints.text"),
    ScalaCodeInsightBundle.message("configure.type.hints.description"),
    null
  ) {
    override def actionPerformed(e: AnActionEvent): Unit = {
      def defaultProject = ProjectManager.getInstance().getDefaultProject
      val project = e.getProject.nullSafe.getOrElse(defaultProject)

      ScalaTypeHintsSettingsModel.navigateTo(project)
    }
  }
}
