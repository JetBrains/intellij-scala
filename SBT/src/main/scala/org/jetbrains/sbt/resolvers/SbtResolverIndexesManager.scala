package org.jetbrains.sbt
package resolvers

import java.io.File

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}

import scala.collection.mutable.{Set => MutableSet}

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */

class SbtResolverIndexesManager(val indexesDir: File) extends Disposable {

  def this() = this(null)

  private val realIndexesDir = if (indexesDir == null) SbtResolverIndexesManager.DEFAULT_INDEXES_DIR else indexesDir
  private val indexes: MutableSet[SbtResolverIndex] = MutableSet.empty

  realIndexesDir.mkdirs()
  if (!realIndexesDir.exists || !realIndexesDir.isDirectory)
    throw new RuntimeException("Can't create indexes dir: %s" format realIndexesDir.absolutePath)

  loadIndexes(realIndexesDir)


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

  // TODO: implement simultaneous updates
  def update(resolvers: Seq[SbtResolver]) {
    val indexes = resolvers map add
    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Indexing resolvers") {
      def run(progressIndicator: ProgressIndicator) {
        val step  = 1.0 / indexes.length
        progressIndicator.setFraction(0.0)
        indexes.foreach (index => {
          progressIndicator.setText(index.root)
          index.update()
          progressIndicator.setFraction(progressIndicator.getFraction + step)
        })
      }
    })
  }

  private def loadIndexes(indexesDir: File) {
    indexesDir.mkdirs()
    if (!indexesDir.exists || !indexesDir.isDirectory)
      throw new RuntimeException("Resolver's indexes dir can not be created: %s" format indexesDir.absolutePath)
    val indices = indexesDir.listFiles()
    if (indices == null) return
    indices foreach (indexDir => if (indexDir.isDirectory) {
        val index = SbtResolverIndex.load(indexDir)
        indexes.add(index)
    })
  }

  private def getIndexDirectory(root: String) = new File(realIndexesDir, root.shaDigest)
}

object SbtResolverIndexesManager {
  def getInstance = ServiceManager.getService(classOf[SbtResolverIndexesManager])

  val DEFAULT_INDEXES_DIR = new File(PathManager.getSystemPath) / "sbt" / "indexes"
}
