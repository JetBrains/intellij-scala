package org.jetbrains.sbt.resolvers

import java.io.File

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.resolvers.indexes.{IvyIndex, ResolverIndex}

import scala.collection.mutable

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
class SbtIndexesManager(val project: Project) extends ProjectComponent {
  import SbtIndexesManager._

  override def projectClosed(): Unit = {
    indexes.values.foreach(_.close())
  }
  override def projectOpened(): Unit = ()
  override def initComponent(): Unit = ()
  override def disposeComponent(): Unit = ()
  override def getComponentName: String = "SbtResolversManager"

  private val indexes = new mutable.HashMap[String, ResolverIndex]()

  private def doUpdateResolverIndexWithProgress(name: String, index: ResolverIndex): Unit = {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating indexes") {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setIndeterminate(true)
        indicator.setText(s"Updating: $name")
        index.doUpdate(Some(indicator))(project)
      }
    })
  }

  def updateWithProgress(resolvers: Seq[SbtResolver]): Unit = {
    resolvers.foreach(r => doUpdateResolverIndexWithProgress(r.name, r.getIndex(project)))
  }

  def updateLocalIvyIndex(): Unit = {
    indexes.values.collect({case i: IvyIndex => doUpdateResolverIndexWithProgress("Local Ivy cache", i)})
  }

  def getIvyIndex(name: String, root: String): ResolverIndex = {
    indexes.getOrElseUpdate(root, createNewIvyIndex(name, root))
  }

  private def createNewIvyIndex(name: String, root: String): ResolverIndex = {
    try {
      new IvyIndex(root, name)
    } catch {
      case e: Throwable =>  // workaround for severe persistent storage corruption
        val cccc = e.getMessage
        val zzz = e.getStackTrace
        cleanUpCorruptedIndex(ResolverIndex.getIndexDirectory(root))
        new IvyIndex(root, name)
    }
  }

}

object SbtIndexesManager {
  def getInstance(project: Project): SbtIndexesManager = project.getComponent(classOf[SbtIndexesManager])

  def cleanUpCorruptedIndex(indexDir: File): Unit = {
    try {
      FileUtil.delete(indexDir)
      notifyWarning(SbtBundle("sbt.resolverIndexer.indexDirIsCorruptedAndRemoved", indexDir.getAbsolutePath))
    } catch {
      case _ : Throwable =>
        notifyWarning(SbtBundle("sbt.resolverIndexer.indexDirIsCorruptedCantBeRemoved", indexDir.getAbsolutePath))
    }
  }

  private def notifyWarning(message: String): Unit =
    NotificationUtil.showMessage(null, message, title = "Resolver Indexer")

}
