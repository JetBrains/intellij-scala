package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import com.intellij.openapi.util.{Getter, Setter}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings.{getInstance => ScalaCodeInsightSettings}
import org.jetbrains.plugins.scala.codeInsight.implicits.ImplicitHints

object ScalaTypeHintsConfigurable {

  import java.lang.{Boolean => JBoolean}

  private def forceHintsUpdateOnNextPass(): Unit = {
    ImplicitHints.updateInAllEditors()
  }

  sealed abstract class ToggleTypeAction(getter: Getter[JBoolean],
                                         setter: Setter[JBoolean]) extends ToggleAction {

    override def isSelected(event: AnActionEvent): Boolean = getter.get()

    override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
      setter.set(value)
      forceHintsUpdateOnNextPass()
    }
  }

  class ToggleTypeHintsAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showTypeHintsGetter,
    ScalaCodeInsightSettings.showTypeHintsSetter
  )

  class ToggleFunctionReturnTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showFunctionReturnTypeGetter,
    ScalaCodeInsightSettings.showFunctionReturnTypeSetter
  )

  class TogglePropertyTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showPropertyTypeGetter,
    ScalaCodeInsightSettings.showPropertyTypeSetter
  )

  class ToggleLocalVariableTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showLocalVariableTypeGetter,
    ScalaCodeInsightSettings.showLocalVariableTypeSetter
  )
  class ToggleObviousTypeAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showObviousTypeGetter,
    ScalaCodeInsightSettings.showObviousTypeSetter
  )

  class ToggleMethodChainInlayHintsAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.showMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.showMethodChainInlayHintsSetter()
  )

  class ToggleAlignMethodChainInlayHintsAction extends ToggleTypeAction(
    ScalaCodeInsightSettings.alignMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.alignMethodChainInlayHintsSetter()
  )
}
