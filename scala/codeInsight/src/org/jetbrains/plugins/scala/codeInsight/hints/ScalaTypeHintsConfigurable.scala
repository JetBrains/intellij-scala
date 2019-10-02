package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.util.{Getter, Setter}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings.{getInstance => ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

final class ScalaTypeHintsConfigurable
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

    checkBox(
      "Show expression chain type hints (Scala)",
      settings.showExpressionChainTypeGetter,
      settings.showExpressionChainTypeSetter
    )
    checkBox(
      "Show obvious types in expression chains (Scala)",
      settings.showObviousTypesInExpressionChainsGetter,
      settings.showObviousTypesInExpressionChainsSetter
    )
    checkBox(
      "Show identical types in expression chains (Scala)",
      settings.showIdenticalTypeInExpressionChainGetter,
      settings.showIdenticalTypeInExpressionChainSetter
    )
    checkBox(
      "Align type hints in expression chains (Scala)",
      settings.alignExpressionChainGetter,
      settings.alignExpressionChainSetter
    )

    checkBox(
      "Move Aligned type hints in expression chains to the right (Scala)",
      settings.moveExpressionChainRightGetter,
      settings.moveExpressionChainRightSetter
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
    ImplicitHints.updateInAllEditors()
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

  class ToggleExpressionChainTypeAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showExpressionChainTypeGetter,
    ScalaCodeInsightSettings.showExpressionChainTypeSetter
  )

  class ToggleObviousTypesInExpressionChainAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showObviousTypesInExpressionChainsGetter,
    ScalaCodeInsightSettings.showObviousTypesInExpressionChainsSetter
  )

  class ToggleIdenticalTypeInExpressionChainChainAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showIdenticalTypeInExpressionChainGetter,
    ScalaCodeInsightSettings.showIdenticalTypeInExpressionChainSetter
  )

  class ToggleAlignExpressionChainAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.alignExpressionChainGetter,
    ScalaCodeInsightSettings.alignExpressionChainSetter
  )

  class ToggleMoveExpressionChainRightAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.moveExpressionChainRightGetter,
    ScalaCodeInsightSettings.moveExpressionChainRightSetter
  )
}
