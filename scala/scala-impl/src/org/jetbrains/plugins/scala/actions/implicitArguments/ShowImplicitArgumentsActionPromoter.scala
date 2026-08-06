package org.jetbrains.plugins.scala.actions.implicitArguments

import com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction
import com.intellij.openapi.actionSystem.{ActionPromoter, AnAction, DataContext}

import java.util
import scala.jdk.CollectionConverters._

class ShowImplicitArgumentsActionPromoter extends ActionPromoter {
  override def promote(actions: util.List[_ <: AnAction], context: DataContext): util.List[AnAction] = {
    val filtered = actions.asScala.filter {
      case _: ShowExpressionTypeAction | _: ShowImplicitArgumentsAction => true
      case _ => false
    }
    if (filtered.nonEmpty)
      filtered.sortBy {
        case _: ShowExpressionTypeAction => 2
        case _: ShowImplicitArgumentsAction => 1
        case _ => 0
      }.asJava.asInstanceOf[util.List[AnAction]]
    else null
  }
}
