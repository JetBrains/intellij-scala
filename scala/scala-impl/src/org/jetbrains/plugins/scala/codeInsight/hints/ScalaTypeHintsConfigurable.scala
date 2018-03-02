package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.util.{Getter, Setter}

class ScalaTypeHintsConfigurable
  extends BeanConfigurable[ScalaCodeInsightSettings](ScalaCodeInsightSettings.getInstance)
    with CodeFoldingOptionsProvider {

  checkBox(
    "Show function return type hints (Scala)",
    getInstance.showFunctionReturnTypeGetter,
    getInstance.showFunctionReturnTypeSetter
  )
  checkBox(
    "Show property type hints (Scala)",
    getInstance.showPropertyTypeGetter,
    getInstance.showPropertyTypeSetter
  )
  checkBox(
    "Show local variable type hints (Scala)",
    getInstance.showPropertyTypeGetter,
    getInstance.showLocalVariableTypeSetter
  )

  override def apply(): Unit = {
    super.apply()
    ScalaTypeHintsPassFactory.forceHintsUpdateOnNextPass()
  }
}

object ScalaTypeHintsConfigurable {

  import java.lang.{Boolean => JBoolean}

  import ScalaCodeInsightSettings.{getInstance => settings}

  sealed abstract class ToogleTypeAction(getter: Getter[JBoolean], setter: Setter[JBoolean]) extends ToggleAction {

    override def isSelected(event: AnActionEvent): Boolean =
      getter.get()

    override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
      setter.set(value)
      ScalaTypeHintsPassFactory.forceHintsUpdateOnNextPass()
    }
  }

  class ToogleFunctionReturnTypeAction extends ToogleTypeAction(
    settings.showFunctionReturnTypeGetter,
    settings.showFunctionReturnTypeSetter
  )

  class TooglePropertyTypeAction extends ToogleTypeAction(
    settings.showPropertyTypeGetter,
    settings.showPropertyTypeSetter
  )

  class ToogleLocalVariableTypeAction extends ToogleTypeAction(
    settings.showLocalVariableTypeGetter,
    settings.showLocalVariableTypeSetter
  )

}
