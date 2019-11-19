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

  sealed abstract class ToogleTypeAction(getter: Getter[JBoolean],
                                         setter: Setter[JBoolean]) extends ToggleAction {

    override def isSelected(event: AnActionEvent): Boolean = getter.get()

    override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
      setter.set(value)
      forceHintsUpdateOnNextPass()
    }
  }

  class ToggleTypeHintsAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showTypeHintsGetter,
    ScalaCodeInsightSettings.showTypeHintsSetter
  )

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

  class ToggleMethodChainInlayHintsAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.showMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.showMethodChainInlayHintsSetter()
  )

  class ToggleAlignMethodChainInlayHintsAction extends ToogleTypeAction(
    ScalaCodeInsightSettings.alignMethodChainInlayHintsGetter(),
    ScalaCodeInsightSettings.alignMethodChainInlayHintsSetter()
  )
}
