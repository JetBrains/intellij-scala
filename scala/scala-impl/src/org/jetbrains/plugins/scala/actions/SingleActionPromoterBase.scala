package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem.{ActionPromoter, AnAction, DataContext}

import java.util
import java.util.Collections

abstract class SingleActionPromoterBase extends ActionPromoter {
  def shouldPromote(anAction: AnAction, context: DataContext): Boolean
  
  override def promote(actions: util.List[_ <: AnAction], context: DataContext): util.List[AnAction] = {
    val it = actions.iterator()

    while (it.hasNext) {
      val a = it.next()
      if (shouldPromote(a, context)) return util.Arrays.asList(a)
    }

    Collections.emptyList()
  }
}
