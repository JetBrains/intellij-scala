package org.jetbrains.plugins.scala.externalHighlighters.compiler
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.Opt

import scala.util.Try

trait LoggingHighlightingCompiler
  extends HighlightingCompiler {

  protected def log(msg: String): Unit

  abstract override def compile(project: Project, source: Document): Opt[Unit] = {
    val result = Try(super.compile(project, source))
    log(s"Highlighting compiler for $source result $result")
    result.get
  }
}
