package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.{getInstance => DaemonCodeAnalyzer}
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.project.ProjectManager.{getInstance => ProjectManager}
import com.intellij.openapi.util.{Getter, Setter}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings.{getInstance => ScalaCodeInsightSettings}

class ScalaTypeHintsConfigurable
  extends BeanConfigurable[ScalaCodeInsightSettings](ScalaCodeInsightSettings)
    with CodeFoldingOptionsProvider {

  {
    val settings = getInstance

    checkBox(
      "Show function return type hints (Scala)",
      settings.showFunctionReturnTypeGetter,
      settings.showFunctionReturnTypeSetter
    )
    checkBox(
      "Show property type hints (Scala)",
      settings.showPropertyTypeGetter,
      settings.showPropertyTypeSetter
    )
    checkBox(
      "Show local variable type hints (Scala)",
      settings.showLocalVariableTypeGetter,
      settings.showLocalVariableTypeSetter
    )

    val settingsPanel = new ScalaTypeHintsSettingsPanel
    component(
      settingsPanel.getPanel,
      settings.presentationLengthGetter,
      settings.presentationLengthSetter,
      settingsPanel.presentationLengthGetter,
      settingsPanel.presentationLengthSetter
    )

    checkBox(
      "Show obvious types (Scala)",
      settings.showObviousTypeGetter,
      settings.showObviousTypeSetter
    )
  }

  override def apply(): Unit = {
    super.apply()
    ScalaTypeHintsConfigurable.forceHintsUpdateOnNextPass()
  }
}

object ScalaTypeHintsConfigurable {

  import java.lang.{Boolean => JBoolean}

  private def forceHintsUpdateOnNextPass(): Unit = {
    ScalaTypeHintsPassFactory.StampHolder.forceHintsUpdateOnNextPass()

    ProjectManager.getOpenProjects
      .map(DaemonCodeAnalyzer)
      .foreach(_.restart())
  }

  sealed abstract class ToogleTypeAction(getter: Getter[JBoolean],
                                         setter: Setter[JBoolean]) extends ToggleAction {

    override def isSelected(event: AnActionEvent): Boolean = getter.get()

    override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
      setter.set(value)
      forceHintsUpdateOnNextPass()
    }
  }

  class ToogleFunctionReturnTypeAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showFunctionReturnTypeGetter,
    ScalaCodeInsightSettings.showFunctionReturnTypeSetter
  )

  class TooglePropertyTypeAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showPropertyTypeGetter,
    ScalaCodeInsightSettings.showPropertyTypeSetter
  )

  class ToogleLocalVariableTypeAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showLocalVariableTypeGetter,
    ScalaCodeInsightSettings.showLocalVariableTypeSetter
  )

  class ToogleObviousTypeAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showObviousTypeGetter,
    ScalaCodeInsightSettings.showObviousTypeSetter
  )

}
