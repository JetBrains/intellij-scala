package org.jetbrains.sbt
package resolvers

import java.io.File

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}

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

    var indexesToUpdate = Seq.empty[SbtResolverIndex]
    updatingIndexes synchronized {
      indexesToUpdate = resolvers filter { r => updatingIndexes.find { r.root == _.root }.isEmpty } map add
      updatingIndexes ++= indexesToUpdate
    }

    if (indexesToUpdate.isEmpty) return

    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Indexing resolvers") {
      def run(progressIndicator: ProgressIndicator) {
        try {
          indexesToUpdate.foreach { index =>
            progressIndicator.setFraction(0.0)
            progressIndicator.setText(index.root)
            index.update(Some(progressIndicator))
            updatingIndexes synchronized {
              updatingIndexes -= index
            }
          }
        } finally {
            updatingIndexes synchronized { updatingIndexes --= indexesToUpdate }
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
        val index = SbtResolverIndex.load(indexDir)
        indexes.add(index)
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
