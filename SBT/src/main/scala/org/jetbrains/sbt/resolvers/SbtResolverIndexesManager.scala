package org.jetbrains.sbt
package resolvers

import java.io.File

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.util.io.FileUtil

import scala.collection.mutable

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */

class SbtResolverIndexesManager(val testIndexesDir: Option[File]) extends Disposable {
  import org.jetbrains.sbt.resolvers.SbtResolverIndexesManager._

  def this() = this(None)

  private val indexesDir = testIndexesDir getOrElse SbtResolverIndexesManager.DEFAULT_INDEXES_DIR
  private val indexes: mutable.Set[SbtResolverIndex] = mutable.Set.empty
  private val updatingIndexes: mutable.Set[SbtResolverIndex] = mutable.Set.empty

  loadIndexes()


  def add(resolver: SbtResolver) = find(resolver) match {
    case Some(index) => index
    case None =>
      val newIndex = SbtResolverIndex.create(resolver.kind, resolver.root, getIndexDirectory(resolver.root))
      indexes.add(newIndex)
      newIndex
  }

  def find(resolver: SbtResolver): Option[SbtResolverIndex] =
    indexes find { _.root == resolver.root }

  def dispose() =
    indexes foreach { _.close() }

  def update(resolvers: Seq[SbtResolver]) {
    def ensureIndexes(resolvers: Seq[SbtResolver]) = {
      resolvers.map { r =>
        try {
          Some(add(r))
        } catch {
          case e: Throwable =>
            notifyError(SbtBundle("sbt.resolverIndexer.creatingError", r.root, e.getMessage))
            None
        }
      }.flatten
    }

    var indexesToUpdate = Seq.empty[SbtResolverIndex]
    updatingIndexes synchronized {
      val notUpdating = resolvers filter { r => updatingIndexes.find { r.root == _.root }.isEmpty }
      indexesToUpdate = ensureIndexes(notUpdating)
      updatingIndexes ++= indexesToUpdate
    }

    if (indexesToUpdate.isEmpty) return

    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Indexing resolvers") {
      def run(progressIndicator: ProgressIndicator) {
        indexesToUpdate.foreach { index =>
          progressIndicator.setFraction(0.0)
          progressIndicator.setText(index.root)
          try {
            index.update(Some(progressIndicator))
          } catch {
            case e: Throwable =>
              notifyError(SbtBundle("sbt.resolverIndexer.updatingError", index.root, e.getMessage))
          } finally {
            updatingIndexes synchronized { updatingIndexes -= index }
          }
        }
      }
    })
  }


  private def loadIndexes() {
    indexesDir.mkdirs()
    if (!indexesDir.exists || !indexesDir.isDirectory) {
      notifyError(SbtBundle("sbt.resolverIndexer.cantCreateIndexesDir", indexesDir.absolutePath))
      return
    }

    val indices = indexesDir.listFiles()
    if (indices == null) return
    indices foreach { indexDir => if (indexDir.isDirectory) {
        try {
          val index = SbtResolverIndex.load(indexDir)
          indexes.add(index)
        } catch {
          case e: Throwable =>
            FileUtil.delete(indexDir)
            notifyWarn(SbtBundle("sbt.resolverIndexer.loadingError", indexDir, e.getMessage))
        }
      }
    }
  }

  private def getIndexDirectory(root: String) = new File(indexesDir, root.shaDigest)
}

object SbtResolverIndexesManager {
  val DEFAULT_INDEXES_DIR = new File(PathManager.getSystemPath) / "sbt" / "indexes"

  def notifyWarn(msg: String) =
    Notifications.Bus.notify(new Notification("sbt", "Resolver Indexer", msg, NotificationType.WARNING))
  def notifyError(msg: String) =
    Notifications.Bus.notify(new Notification("sbt", "Resolver Indexer", msg, NotificationType.ERROR))

  def apply() = ServiceManager.getService(classOf[SbtResolverIndexesManager])
}
