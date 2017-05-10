package org.jetbrains.plugins.scala.lang.macros.expansion

import java.io._

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.{CompilationStatusListener, CompileContext, CompilerManager}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugin.scala.util.MacroExpansion

import scala.collection.mutable

/**
  * @author Mikhail Mutcianko
  * @since 20.09.16
  */
class ReflectExpansionsCollector(project: Project) extends AbstractProjectComponent(project) {
  override def getComponentName = "ReflectExpansionsCollector"

  override def projectOpened(): Unit = {
    installCompilationListener()
    collectedExpansions = deserializeExpansions()
  }
  override def projectClosed(): Unit = uninstallCompilationListener()

  private var collectedExpansions: mutable.Map[String, Seq[MacroExpansion]] = mutable.Map.empty

  private val compilationStatusListener = new CompilationStatusListener {
    override def compilationFinished(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext): Unit = {
      collectedExpansions = deserializeExpansions()
    }
  }

  private def installCompilationListener() = {
    CompilerManager.getInstance(project).addCompilationStatusListener(compilationStatusListener)
  }

  private def uninstallCompilationListener() = {
    CompilerManager.getInstance(project).removeCompilationStatusListener(compilationStatusListener)
  }

  private def deserializeExpansions(): mutable.Map[String, Seq[MacroExpansion]] = {
    val file = new File(PathManager.getSystemPath + s"/expansion-${project.getName}")
    if (!file.exists()) return mutable.Map.empty
    val fs = new BufferedInputStream(new FileInputStream(file))
    val os = new ObjectInputStream(fs)
    val res = mutable.Map[String, Seq[MacroExpansion]]()
    try {
      while (fs.available() > 0) {
        val expansion = os.readObject().asInstanceOf[MacroExpansion]
        res.update(expansion.place.sourceFile, res.getOrElse(expansion.place.sourceFile, Seq.empty) :+ expansion)
      }
    } catch {
      case _: InvalidClassException => FileUtil.delete(file) // signatures have changed, simply drop the caches
    } finally {
      os.close()
    }
    res
  }

  def getExpansion(elem: PsiElement): Option[String] = {
    val expansions = collectedExpansions.getOrElse(elem.getContainingFile.getVirtualFile.getPath, Seq.empty)
    expansions.find(_.place.offset == elem.getTextOffset).map(_.body)
  }

}

object ReflectExpansionsCollector {
  def getInstance(project: Project): ReflectExpansionsCollector = project.getComponent(classOf[ReflectExpansionsCollector])
}
