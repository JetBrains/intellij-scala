package org.jetbrains.plugins.scala.project.template

import com.intellij.platform.ProjectTemplatesFactory
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

abstract class ScalaProjectTemplatesFactoryBase extends ProjectTemplatesFactory {

  override def getGroupIcon(group: String): Icon = Icons.SCALA_SMALL_LOGO

  override final def getGroups: Array[String] = {
    // Do not show "Scala" group if NPW is enabled
    // Fallback to the "old" NPW is no longer available
    Array.empty
  }
}
