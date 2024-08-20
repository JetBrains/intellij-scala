package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
trait NewScalaFileActionExtension {
  def isAvailable(dataContext: DataContext): Boolean
}

object NewScalaFileActionExtension {
  private val EpName = ExtensionPointName.create[NewScalaFileActionExtension]("org.intellij.scala.newScalaFileActionExtension")

  def isAvailable(dataContext: DataContext): Boolean = {
    EpName.getExtensions.iterator.exists(_.isAvailable(dataContext))
  }
}