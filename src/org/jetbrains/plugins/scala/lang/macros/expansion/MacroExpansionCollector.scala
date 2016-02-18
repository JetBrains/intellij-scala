package org.jetbrains.plugins.scala.lang.macros.expansion

import com.intellij.openapi.compiler.{CompileContext, CompilationStatusListener, CompilerManager}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

class MacroExpansionCollector(private val project: Project) extends ProjectComponent {

  private val compilationStatusListener = new CompilationStatusListener {
    override def fileGenerated(outputRoot: String, relativePath: String): Unit = ???

    override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) = {
      println(compileContext)
    }
  }

  override def projectOpened() = {
    CompilerManager.getInstance(project).addCompilationStatusListener(compilationStatusListener)
  }

  override def projectClosed() = {
    CompilerManager.getInstance(project).removeCompilationStatusListener(compilationStatusListener)
  }

  override def initComponent(): Unit = ()

  override def disposeComponent(): Unit = ()

  override def getComponentName = "MacroExpansionCollector"
}
