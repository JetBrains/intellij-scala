package org.jetbrains.plugins.scala.project

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle

object CompileOrderDescriptions {
  @Nls
  def get(compileOrder: CompileOrder): String = compileOrder match {
    case CompileOrder.Mixed         => ScalaBundle.message("compile.order.mixed")
    case CompileOrder.JavaThenScala => ScalaBundle.message("compile.order.java.then.scala")
    case CompileOrder.ScalaThenJava => ScalaBundle.message("compile.order.scala.then.java")
  }
}
