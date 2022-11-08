package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType

abstract class ScalaMacro extends Macro {

  def getNameShort: String

  override final def getName: String = "scala_" + getNameShort

  override def getPresentableName: String = getNameShort + "()"

  override final def isAcceptableInContext(context: TemplateContextType): Boolean =
    context match {
      case _: ScalaCodeContextType => true
      case _ => false
    }
}

object ScalaMacro {
  private[macros] val DefaultValue = "a"
}
