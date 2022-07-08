package org.jetbrains.plugins.scala.lang.macros.expansion

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.util.{MacroExpansion, Place}

import java.io._
import scala.collection.mutable
import scala.util.Using

class ReflectExpansionsCollector(project: Project) {
  import ReflectExpansionsCollector._

  private val collectedExpansions: mutable.HashMap[Place, MacroExpansion] = mutable.HashMap.empty
  private var parser: ScalaReflectMacroExpansionParser = _
  private val LOG = Logger.getInstance(classOf[ReflectExpansionsCollector])


  deserializeExpansions()

  def getExpansion(elem: PsiElement): Option[MacroExpansion] = {
    val offset = PsiTreeUtil.getParentOfType(elem, classOf[ScAnnotation]) match {
      case _: ScAnnotation => elem.getTextOffset
      case _ => elem.getNode.getTextRange.getEndOffset
    }
    val path = elem.getContainingFile.getVirtualFile.getPath
    val place = Place(path, offset)()
    collectedExpansions.get(place)
  }

  def processCompilerMessage(text: String): Unit = {
    parser.processMessage(text)
  }

  def compilationStarted(): Unit = {
    collectedExpansions.clear()
    parser = new ScalaReflectMacroExpansionParser(project.getName)
  }

  def compilationFinished(): Unit = {
    parser.expansions.foreach { exp => collectedExpansions += exp.place -> exp }
    serializeExpansions()
    if (collectedExpansions.nonEmpty)
      invokeLater(restartAnalyzer(project))
  }

  private def deserializeExpansions(): Unit = {
    val file = new File(System.getProperty("java.io.tmpdir") + s"/expansion-${project.getName}")

    if (!file.exists())
      return

    try {
      Using.resource(new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) { os =>
        collectedExpansions ++= os.readObject().asInstanceOf[collectedExpansions.type]
      }
    } catch {
      case e: Throwable =>
        LOG.warn("Filed to deserialize macro expansions, removing cache", e)
        file.delete()
    }
  }

  def serializeExpansions(): Unit = {
    val file = new File(System.getProperty("java.io.tmpdir") + s"/expansion-${project.getName}")
    try {
      Using.resource(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
        _.writeObject(collectedExpansions)
      }
    } catch {
      case e: Throwable =>
        LOG.warn("Filed to serialize macro expansions, removing cache", e)
        collectedExpansions.clear()
        file.delete()
    }
  }

}

object ReflectExpansionsCollector {

  def restartAnalyzer(project: Project): Unit = {
    if (project == null || project.isDisposed) return

    FileEditorManager.getInstance(project).getSelectedEditors.filter(_.isValid).foreach { editor =>
      val analyzer = DaemonCodeAnalyzer.getInstance(project)
      val psiManager = PsiManager.getInstance(project)
      Option(psiManager.findFile(editor.getFile)).map(analyzer.restart)
    }
  }

  def getInstance(project: Project): ReflectExpansionsCollector =
    project.getService(classOf[ReflectExpansionsCollector])
}
