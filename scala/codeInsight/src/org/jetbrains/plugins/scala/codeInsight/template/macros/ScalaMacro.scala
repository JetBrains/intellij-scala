package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._

/**
  * @author adkozlov
  */
abstract class ScalaMacro extends Macro {

  override final def isAcceptableInContext(context: TemplateContextType): Boolean = context match {
    case _: template.impl.ScalaCodeContextType => true
    case _ => false
  }

  override final def getName: String = "scala_" + getPresentableName.replaceFirst("\\(.*\\)$", "")
}

object ScalaMacro {

  private[macros] val DefaultValue = "a"
}
