package org.jetbrains.plugins.scala
package console

import com.intellij.openapi.actionSystem.{DataContext, AnAction, ActionPromoter}
import java.util
import java.util.Collections

/**
 * User: Dmitry Naydanov
 * Date: 11/14/13
 */
class ExecuteInConsoleActionPromoter extends ActionPromoter {
  def promote(actions: util.List[AnAction], context: DataContext): util.List[AnAction] = {
    for (i <- 0 until actions.size()) {
      val action: AnAction = actions.get(i)
      
      if (action.isInstanceOf[ScalaConsoleExecuteAction]) {
        val promoted = new util.ArrayList[AnAction](1)
        promoted add action
        return promoted
      }
    }
    
    Collections.emptyList()
  }
}
