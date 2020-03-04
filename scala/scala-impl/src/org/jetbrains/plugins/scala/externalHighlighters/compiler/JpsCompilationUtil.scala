package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.RescheduledExecutor

object JpsCompilationUtil {

  private val jpsCompiler: JpsCompiler = new JpsCompilerImpl
  private val jpsCompilerExecutor = new RescheduledExecutor("CompileJpsExecutor")

  def saveDocumentAndCompileProject(document: Option[Document],
                                    project: Project): Unit =
    jpsCompilerExecutor.schedule(ScalaHighlightingMode.compilationJpsDelay) {
      invokeAndWait {
        document.foreach(FileDocumentManager.getInstance.saveDocument)
      }
      jpsCompiler.compile(project)
    }
}
