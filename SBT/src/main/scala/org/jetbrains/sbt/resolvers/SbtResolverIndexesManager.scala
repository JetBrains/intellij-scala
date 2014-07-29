package org.jetbrains.sbt
package resolvers

import java.io.File

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.util.io.FileUtil

import scala.collection.mutable.{Set => MutableSet}

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */

class SbtResolverIndexesManager(val testIndexesDir: File) extends Disposable {
  import org.jetbrains.sbt.resolvers.SbtResolverIndexesManager._

  def this() = this(null)

  private val indexesDir = if (testIndexesDir == null) SbtResolverIndexesManager.DEFAULT_INDEXES_DIR else testIndexesDir
  private val indexes: MutableSet[SbtResolverIndex] = MutableSet.empty
  private val updatingIndexes: MutableSet[SbtResolverIndex] = MutableSet.empty

  loadIndexes()


  def add(resolver: SbtResolver) = find(resolver) match {
    case Some(index) => index
    case None =>
      val newIndex = SbtResolverIndex.create(resolver.root, getIndexDirectory(resolver.root))
      indexes.add(newIndex)
      newIndex
  }

  def find(resolver: SbtResolver): Option[SbtResolverIndex] =
    indexes.find(_.root == resolver.root)

  def dispose() {
    indexes foreach (_.close())
  }

  def update(resolvers: Seq[SbtResolver]) {
    def ensureIndexes(resolvers: Seq[SbtResolver]) = {
      resolvers.map(r => {
        try {
          Some(add(r))
        } catch {
          case e: Throwable =>
            notifyError(CREATING_ERROR_MSG.format(r.root, e.getMessage))
            None
        }
      }).flatten
    }

    var indexesToUpdate = Seq.empty[SbtResolverIndex]
    updatingIndexes.synchronized({
      val notUpdating = resolvers filter (r => updatingIndexes.find(r.root == _.root).isEmpty)
      indexesToUpdate = ensureIndexes(notUpdating)
      updatingIndexes ++= indexesToUpdate
    })

    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Indexing resolvers") {
      def run(progressIndicator: ProgressIndicator) {
        indexesToUpdate.foreach (index => {
          progressIndicator.setFraction(0.0)
          progressIndicator.setText(index.root)
          try {
            index.update(Some(progressIndicator))
          } catch {
            case e: Throwable =>
              notifyError(UPDATING_ERROR_MSG.format(index.root, e.getMessage))
          } finally {
            updatingIndexes.synchronized(updatingIndexes -= index)
          }
        })
      }
    })
  }


  private def loadIndexes() {
    indexesDir.mkdirs()
    if (!indexesDir.exists || !indexesDir.isDirectory) {
      notifyError(INDEX_DIR_CREATING_ERROR_MSG format indexesDir.absolutePath)
      return
    }

    val indices = indexesDir.listFiles()
    if (indices == null) return
    indices foreach (indexDir => if (indexDir.isDirectory) {
        try {
          val index = SbtResolverIndex.load(indexDir)
          indexes.add(index)
        } catch {
          case e: Throwable =>
            FileUtil.delete(indexDir)
            notifyWarn(LOADING_ERROR_MSG.format(indexDir, e.getMessage))
        }
    })
  }

  private def getIndexDirectory(root: String) = new File(indexesDir, root.shaDigest)
}

object SbtResolverIndexesManager {
  val DEFAULT_INDEXES_DIR = new File(PathManager.getSystemPath) / "sbt" / "indexes"

  val LOADING_ERROR_MSG = "Error while loading index at %s\n%s\nCorrupted index will be deleted"
  val UPDATING_ERROR_MSG = "Error while updating index: %s\n%s"
  val CREATING_ERROR_MSG = "Error while creating index: %s\n%s"
  val INDEX_DIR_CREATING_ERROR_MSG = "Indexes dir can not be created: %s"

  def notifyWarn(msg: String) =
    Notifications.Bus.notify(new Notification("sbt", "Resolver Indexer", msg, NotificationType.WARNING))
  def notifyError(msg: String) =
    Notifications.Bus.notify(new Notification("sbt", "Resolver Indexer", msg, NotificationType.ERROR))

  def getInstance = ServiceManager.getService(classOf[SbtResolverIndexesManager])
}
