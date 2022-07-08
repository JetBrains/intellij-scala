package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._

abstract class ScalaMacro extends Macro {

  // 🔅 is added when -Didea.l10n=true flag is used
  // TODO: better not to depend on presentable name, it can be localized in theory
  override final def getName: String = "scala_" + getPresentableName.replaceAll("""\(.*\)""", "").replace("🔅", "")

  override final def isAcceptableInContext(context: TemplateContextType): Boolean = context match {
    case _: template.impl.ScalaCodeContextType => true
    case _ => false
  }
}

object ScalaMacro {
  private[macros] val DefaultValue = "a"
}
