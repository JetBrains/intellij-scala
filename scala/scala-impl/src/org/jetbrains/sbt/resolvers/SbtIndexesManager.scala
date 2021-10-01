package org.jetbrains.sbt.resolvers

import java.io.File
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.Ivy2IndexingMode
import org.jetbrains.plugins.scala.util.NotificationUtil
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex.FORCE_UPDATE_KEY
import org.jetbrains.sbt.resolvers.indexes.{IvyIndex, ResolverIndex}

import scala.collection.mutable

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
final class SbtIndexesManager(val project: Project) extends Disposable {
  import SbtIndexesManager._

  private val LOG = Logger.getInstance(classOf[SbtIndexesManager])

  override def dispose(): Unit = {
    indexes.values.foreach(_.close())
  }

  private val indexes = new mutable.HashMap[String, ResolverIndex]()

  def doUpdateResolverIndexWithProgress(@Nls name: String, index: ResolverIndex): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode && sys.props.get(FORCE_UPDATE_KEY).isEmpty)
      return

    if (!project.isDisposed) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, SbtBundle.message("title.updating.indices")) {
        override def run(indicator: ProgressIndicator): Unit = {
          indicator.setIndeterminate(true)
          indicator.setText(SbtBundle.message("indicator.updating.name", name))
          try {
            index.doUpdate(Some(indicator))
          } catch {
            case original:Exception =>
              try {
                index.close()
              } catch {
                case pce: ProcessCanceledException => throw pce
                case e:Exception =>
                  e.addSuppressed(original)
                  LOG.error("Error while closing index while recovering from another error", e)
              }
              try {
                indicator.setText(SbtBundle.message("indicator.force.rebuilding.dependency.index"))
                FileUtil.delete(ResolverIndex.indexesDir)
                index.doUpdate(Some(indicator))
              } catch {
                case c: ControlFlowException => throw c
                case e: Exception =>
                  ScalaProjectSettings.getInstance(project).setIvy2IndexingMode(Ivy2IndexingMode.Disabled)
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
    } doUpdateResolverIndexWithProgress(resolver.presentableName, index)

  def getIvyIndex(name: String, root: String): ResolverIndex = {
    indexes.getOrElseUpdate(root, createNewIvyIndex(name, root))
  }

  private def createNewIvyIndex(name: String, root: String): ResolverIndex = {
    try {
      new IvyIndex(root, name, project)
    } catch {
      case c: ControlFlowException => throw c
      case e: Throwable =>  // workaround for severe persistent storage corruption
        val cccc = e.getMessage
        val zzz = e.getStackTrace
        cleanUpCorruptedIndex(ResolverIndex.getIndexDirectory(root))
        new IvyIndex(root, name, project)
    }
  }

  private var updateScheduled = false
  def scheduleLocalIvyIndexUpdate(resolver: SbtResolver): Unit = {
    if (ScalaProjectSettings.getInstance(project).getIvy2IndexingMode == Ivy2IndexingMode.Disabled ||
        (ApplicationManager.getApplication.isUnitTestMode && sys.props.get(FORCE_UPDATE_KEY).isEmpty))
      return
    if (!updateScheduled) {
      updateScheduled = true
      extensions.invokeLater {
        val idx = getIvyIndex(resolver.name, resolver.root)
        doUpdateResolverIndexWithProgress(SbtBundle.message("local.ivy.cache"), idx)
        updateScheduled = false
      }
    }
  }

}

object SbtIndexesManager {
  def getInstance(project: Project): Option[SbtIndexesManager] =
    Option(project.getService(classOf[SbtIndexesManager]))

  def cleanUpCorruptedIndex(indexDir: File): Unit = {
    try {
      FileUtil.delete(indexDir)
    } catch {
      case c: ControlFlowException => throw c
      case _ : Throwable =>
        notifyWarning(SbtBundle.message("sbt.resolverIndexer.indexDirIsCorruptedCantBeRemoved", indexDir.getAbsolutePath))
    }
  }

  private def notifyWarning(@Nls message: String): Unit =
    NotificationUtil.showMessage(null, message, title = SbtBundle.message("title.resolver.indexer"))

}
