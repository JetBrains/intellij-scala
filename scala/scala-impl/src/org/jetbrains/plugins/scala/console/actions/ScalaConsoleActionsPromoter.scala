package org.jetbrains.plugins.scala.console.actions

import java.util
import java.util.stream.Collectors

import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.{ActionPromoter, AnAction, CommonDataKeys, DataContext}
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo
import org.jetbrains.plugins.scala.extensions.ObjectExt

class ScalaConsoleActionsPromoter extends ActionPromoter {
  override def promote(actions: util.List[AnAction], context: DataContext): util.List[AnAction] = {
    val isScalaConsoleEditor = Option(context.getData(CommonDataKeys.EDITOR)).exists(ScalaConsoleInfo.isConsole)
    if (isScalaConsoleEditor) {
      actions.stream()
        .filter(_.is[
          ScalaConsoleExecuteAction,
          ScalaConsoleNewLineAction,
          ChooseItemAction
        ])
        .collect(Collectors.toList())
    } else {
      null
    }
  }
}
