package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType

/**
  * @author adkozlov
  */
abstract class ScalaMacro(nameKey: String) extends Macro {

  import ScalaMacro._

  override final def getName: String = getPresentableName.replaceFirst("\\(.*\\)$", "")

  override final def getPresentableName: String = NamePrefix + message(nameKey)

  override final def isAcceptableInContext(context: TemplateContextType): Boolean =
    context match {
      case _: ScalaCodeContextType => true
      case _ => false
    }

  protected def message(nameKey: String): String = CodeInsightBundle.message(nameKey)
}

object ScalaMacro {

  private[macros] val NamePrefix = "scala_"
  private[macros] val DefaultValue = "a"

  private[macros] def message(nameKey: String) = ScalaBundle.message(nameKey)
}
