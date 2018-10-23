package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, DefaultActionGroup}
import com.intellij.psi.PsiElement


package object menu {
  def implicitConversion(e: PsiElement): ActionGroup = {
    val group = new DefaultActionGroup()
    group.add(new MakeConversionExplicit(e))
    group.addSeparator()
    group.add(ActionManager.getInstance().getAction(ShowImplicitHintsAction.Id))
    group.add(ActionManager.getInstance().getAction(ExpandImplicitHintsAction.Id))
    group
  }

  def implicitArguments(e: PsiElement): ActionGroup = {
    val group = new DefaultActionGroup()
    group.add(new ImplicitArgumentsPopup(e))
    group.addSeparator()
    group.add(ActionManager.getInstance().getAction(ShowImplicitHintsAction.Id))
    group.add(ActionManager.getInstance().getAction(ExpandImplicitHintsAction.Id))
    group
  }

  def explicitArguments(e: PsiElement): ActionGroup = {
    val group = new DefaultActionGroup()
    group.add(new RemoveExplicitArguments(e))
    group.addSeparator()
    group.add(ActionManager.getInstance().getAction(ShowImplicitHintsAction.Id))
    group.add(ActionManager.getInstance().getAction(ExpandImplicitHintsAction.Id))
    group
  }
}
