package org.jetbrains.plugins.scala

import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.PluginId

import scala.reflect.{ClassTag, classTag}

package object actions {

  implicit class ActionManagerExExt(private val actionManagerEx: ActionManagerEx)
    extends AnyVal {

    def getScalaActionOfType[A <: AnAction : ClassTag]: A = {
      val result = actionManagerEx.getPluginActions(PluginId.getId("org.intellij.scala"))
        .map(actionManagerEx.getAction)
        .collect {
          case action: A => action
        }
      result.length match {
        case 1 => result.head
        case 0 => throw new IllegalArgumentException(s"No such action: $classTag")
        case n => throw new IllegalArgumentException(s"There are $n actions of type: $classTag")
      }
    }
  }
}
