package org.jetbrains.plugins.scala
package project.template

import com.intellij.framework.FrameworkTypeEx
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
class ScalaFrameworkType extends FrameworkTypeEx("Scala") {
  override def getIcon = Icons.SCALA_SMALL_LOGO

  override def getPresentableName = "Scala"

  override def createProvider() = new ScalaSupportProvider()
}

object ScalaFrameworkType {
  val Instance: ScalaFrameworkType = FrameworkTypeEx.EP_NAME.findExtension(classOf[ScalaFrameworkType])
}