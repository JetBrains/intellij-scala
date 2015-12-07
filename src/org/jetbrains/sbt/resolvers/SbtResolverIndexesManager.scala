package org.jetbrains.sbt
package resolvers

import java.io.{IOException, File}

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.PersistentEnumeratorBase
import org.apache.lucene.store.LockReleaseFailedException
import org.jetbrains.plugins.scala.util.NotificationUtil

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
      indexesToUpdate = resolvers.filterNot(r => updatingIndexes.exists(r.root == _.root)).map(add)
      updatingIndexes ++= indexesToUpdate
    }

    if (indexesToUpdate.isEmpty) return

    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Indexing resolvers") {
      def run(progressIndicator: ProgressIndicator): Unit =
        indexesToUpdate.foreach { index =>
          progressIndicator.setFraction(0.0)
          progressIndicator.setText(index.root)
          try {
            index.update(Some(progressIndicator))
          } catch {
            case exc : ResolverException =>
              notifyWarning(exc.getMessage)
            case exc : LockReleaseFailedException =>
              notifyWarning(SbtBundle("sbt.resolverIndexer.luceneLockException", exc.getMessage))
          } finally {
            updatingIndexes synchronized {
              updatingIndexes -= index
            }
          }
        }
    })
  }

  private def loadIndexes() {
    indexesDir.mkdirs()
    if (!indexesDir.exists || !indexesDir.isDirectory) {
      notifyWarning(SbtBundle("sbt.resolverIndexer.cantCreateIndexesDir", indexesDir.absolutePath))
      return
    }

    val indices = indexesDir.listFiles()
    if (indices == null) return
    indices foreach { indexDir =>
      if (indexDir.isDirectory) {
        try {
          val index = SbtResolverIndex.load(indexDir)
          indexes.add(index)
        } catch {
          case exc : ResolverException =>
            notifyWarning(exc.getMessage)
          case _: PersistentEnumeratorBase.CorruptedException | _: IOException =>
            cleanUpCorruptedIndex(indexDir)
        }
      }
    }
  }

  private def cleanUpCorruptedIndex(indexDir: File): Unit = {
    try {
      FileUtil.delete(indexDir)
      notifyWarning(SbtBundle("sbt.resolverIndexer.indexDirIsCorruptedAndRemoved", indexDir.getAbsolutePath))
    } catch {
      case _ : Throwable =>
        notifyWarning(SbtBundle("sbt.resolverIndexer.indexDirIsCorruptedCantBeRemoved", indexDir.getAbsolutePath))
    }
  }

  private def getIndexDirectory(root: String) = new File(indexesDir, root.shaDigest)
}

object SbtResolverIndexesManager {
  val DEFAULT_INDEXES_DIR = new File(PathManager.getSystemPath) / "sbt" / "indexes"

  def notifyWarning(message: String) =
    NotificationUtil.showMessage(null, message, title = "Resolver Indexer")

  def apply() = ServiceManager.getService(classOf[SbtResolverIndexesManager])
}
