package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.util.RescheduledExecutor
import org.jetbrains.plugins.scala.editor.DocumentExt

object JpsCompilationUtil {

  private val jpsCompiler: JpsCompiler = new JpsCompilerImpl
  private val jpsCompilerExecutor = new RescheduledExecutor("CompileJpsExecutor")

  def syncDocumentAndCompileProject(document: Option[Document],
                                    project: Project): Unit = {
    CompileServerLauncher.ensureServerRunning(project)
    jpsCompilerExecutor.schedule(ScalaHighlightingMode.compilationJpsDelay) {
      document.foreach(_.syncToDisk(project))
      jpsCompiler.compile(project)
    }
  }
}
