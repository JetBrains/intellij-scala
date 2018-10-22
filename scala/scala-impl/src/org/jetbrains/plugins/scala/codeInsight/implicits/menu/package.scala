package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, AnAction, DefaultActionGroup}
import org.jetbrains.plugins.scala.actions.implicitArguments.ShowImplicitArgumentsAction


package object menu {
  val ImplicitConversion: ActionGroup = {
    val group = new DefaultActionGroup()
    group.add(new MakeConversionExplicit())
    group.addSeparator()
    group.add(ActionManager.getInstance().getAction(ShowImplicitHintsAction.Id))
    group.add(ActionManager.getInstance().getAction(ExpandImplicitHintsAction.Id))
    group
  }

  val ImplicitArguments: ActionGroup = {
    val group = new DefaultActionGroup()
    group.add(new ShowImplicitArgumentsAction())
    group.addSeparator()
    group.add(ActionManager.getInstance().getAction(ShowImplicitHintsAction.Id))
    group.add(ActionManager.getInstance().getAction(ExpandImplicitHintsAction.Id))
    group
  }

  val ExplicitArguments: ActionGroup = {
    val group = new DefaultActionGroup()
    group.add(new RemoveExplicitArguments())
    group.addSeparator()
    group.add(ActionManager.getInstance().getAction(ShowImplicitHintsAction.Id))
    group.add(ActionManager.getInstance().getAction(ExpandImplicitHintsAction.Id))
    group
  }
}
