package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.util.{Getter, Setter}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings.{getInstance => settings}

class ScalaTypeHintsConfigurable
  extends BeanConfigurable[ScalaCodeInsightSettings](settings)
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
  }

  override def apply(): Unit = {
    super.apply()
    ScalaTypeHintsPassFactory.forceHintsUpdateOnNextPass()
  }
}

object ScalaTypeHintsConfigurable {

  import java.lang.{Boolean => JBoolean}

  sealed abstract class ToogleTypeAction(getter: Getter[JBoolean],
                                         setter: Setter[JBoolean]) extends ToggleAction {

    override def isSelected(event: AnActionEvent): Boolean = getter.get()

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
