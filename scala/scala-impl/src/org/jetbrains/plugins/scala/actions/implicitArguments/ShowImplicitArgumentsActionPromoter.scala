package org.jetbrains.plugins.scala.actions.implicitArguments

import java.util

import com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction
import com.intellij.openapi.actionSystem.{ActionPromoter, AnAction, DataContext}

import scala.jdk.CollectionConverters._

class ShowImplicitArgumentsActionPromoter extends ActionPromoter {
  override def promote(actions: util.List[AnAction], context: DataContext): util.List[AnAction] =
    actions.asScala.sortBy {
      case _: ShowExpressionTypeAction    => 2
      case _: ShowImplicitArgumentsAction => 1
      case _                              => 0
    }.asJava
}
