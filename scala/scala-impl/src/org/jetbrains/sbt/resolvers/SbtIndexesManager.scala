package org.jetbrains.sbt.resolvers

import java.io.File

import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.resolvers.indexes.{IvyIndex, ResolverIndex}

import scala.collection.mutable

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
class SbtIndexesManager(val project: Project) extends AbstractProjectComponent(project) {
  import SbtIndexesManager._

  private val LOG = Logger.getInstance(classOf[this.type])

  override def projectClosed(): Unit = {
    indexes.values.foreach(_.close())
  }

  override def getComponentName: String = "SbtResolversManager"

  private val indexes = new mutable.HashMap[String, ResolverIndex]()

  def doUpdateResolverIndexWithProgress(name: String, index: ResolverIndex): Unit = {
    if (!project.isDisposed) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating indexes") {
        override def run(indicator: ProgressIndicator): Unit = {
          indicator.setIndeterminate(true)
          indicator.setText(s"Updating: $name")
          try {
            index.doUpdate(Some(indicator))
          } catch {
            case original:Exception =>
              try {
                index.close()
              } catch {
                case e:Exception =>
                  e.addSuppressed(original)
                  LOG.error("Error while closing index while recovering from another error", e)
              }
              try {
                indicator.setText("Force rebuilding dependency index")
                FileUtil.delete(ResolverIndex.indexesDir)
                index.doUpdate(Some(indicator))
              } catch {
                case e: Exception =>
                  ScalaProjectSettings.getInstance(project).setEnableLocalDependencyIndexing(false)
                  e.addSuppressed(original)
                  LOG.error("Can't rebuild index after force cleaning, disabling artifact indexing", e)
              }
          }
        }
      })
    }
  }

  def updateWithProgress(resolvers: Seq[SbtResolver]): Unit =
  for {
    resolver <- resolvers
    index <- resolver.getIndex(project)
  } doUpdateResolverIndexWithProgress(resolver.name, index)

  def getIvyIndex(name: String, root: String): ResolverIndex = {
    indexes.getOrElseUpdate(root, createNewIvyIndex(name, root))
  }

  private def createNewIvyIndex(name: String, root: String): ResolverIndex = {
    try {
      new IvyIndex(root, name, project)
    } catch {
      case e: Throwable =>  // workaround for severe persistent storage corruption
        val cccc = e.getMessage
        val zzz = e.getStackTrace
        cleanUpCorruptedIndex(ResolverIndex.getIndexDirectory(root))
        new IvyIndex(root, name, project)
    }
  }

  private var updateScheduled = false
  def scheduleLocalIvyIndexUpdate(resolver: SbtResolver): Unit = {
    if (!ScalaProjectSettings.getInstance(project).isEnableLocalDependencyIndexing)
      return
    if (!updateScheduled) {
      updateScheduled = true
      extensions.invokeLater {
        val idx = getIvyIndex(resolver.name, resolver.root)
        doUpdateResolverIndexWithProgress("Local Ivy cache", idx)
        updateScheduled = false
      }
    }
  }

}

object SbtIndexesManager {

  def getInstance(project: Project): Option[SbtIndexesManager] = Option(project.getComponent(classOf[SbtIndexesManager]))

  def cleanUpCorruptedIndex(indexDir: File): Unit = {
    try {
      FileUtil.delete(indexDir)
//      notifyWarning(SbtBundle("sbt.resolverIndexer.indexDirIsCorruptedAndRemoved", indexDir.getAbsolutePath))
    } catch {
      case _ : Throwable =>
        notifyWarning(SbtBundle("sbt.resolverIndexer.indexDirIsCorruptedCantBeRemoved", indexDir.getAbsolutePath))
    }
  }

  private def notifyWarning(message: String): Unit =
    NotificationUtil.showMessage(null, message, title = "Resolver Indexer")

}
