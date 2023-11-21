package org.jetbrains.plugins.scala.console.actions

import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.{ActionPromoter, AnAction, CommonDataKeys, DataContext}
import org.jetbrains.plugins.scala.console.ScalaConsoleInfo
import org.jetbrains.plugins.scala.extensions.ObjectExt

import java.util
import scala.jdk.CollectionConverters.*

class ScalaConsoleActionsPromoter extends ActionPromoter {
  //noinspection ScalaRedundantCast
  override def promote(actions: util.List[_ <: AnAction], context: DataContext): util.List[AnAction] = {
    val isScalaConsoleEditor = Option(context.getData(CommonDataKeys.EDITOR)).exists(ScalaConsoleInfo.isConsole)
    if (isScalaConsoleEditor) {
      actions.asScala
        .filter((action: AnAction) => action.is[
          ScalaConsoleExecuteAction,
          ScalaConsoleNewLineAction,
          ScalaConsoleCleanAction,
          ChooseItemAction
        ])
        .asJava
        .asInstanceOf[util.List[AnAction]]
    } else {
      null
    }
  }
}