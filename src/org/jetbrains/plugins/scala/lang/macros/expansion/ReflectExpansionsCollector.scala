package org.jetbrains.plugins.scala.lang.macros.expansion

import java.io.{BufferedInputStream, File, FileInputStream, ObjectInputStream}

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerManager}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugin.scala.util.MacroExpansion

/**
  * @author Mikhail Mutcianko
  * @since 20.09.16
  */
class ReflectExpansionsCollector(private val project: Project) extends ProjectComponent {
  override def getComponentName = "ReflectExpansionsCollector"
  override def initComponent() = ()
  override def disposeComponent() = ()
  override def projectOpened() = {
    installCompilationListener()
    collectedExpansions = deserializeExpansions()
  }
  override def projectClosed() = uninstallCompilationListener()

  private var collectedExpansions: Seq[MacroExpansion] = Seq.empty

  private val compilationStatusListener = new CompilationStatusListener {
    override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext): Unit = {
      collectedExpansions = deserializeExpansions()
      println(collectedExpansions)
    }
  }

  private def installCompilationListener() = {
    CompilerManager.getInstance(project).addCompilationStatusListener(compilationStatusListener)
  }

  private def uninstallCompilationListener() = {
    CompilerManager.getInstance(project).removeCompilationStatusListener(compilationStatusListener)
  }

  private def deserializeExpansions(): Seq[MacroExpansion] = {
    val file = new File(PathManager.getSystemPath + s"/expansion-${project.getName}")
    if (!file.exists()) return Seq.empty
    val fs = new BufferedInputStream(new FileInputStream(file))
    val os = new ObjectInputStream(fs)
    val res = scala.collection.mutable.ListBuffer[MacroExpansion]()
    while (fs.available() > 0) {
      res += os.readObject().asInstanceOf[MacroExpansion]
    }
    res
  }

}
