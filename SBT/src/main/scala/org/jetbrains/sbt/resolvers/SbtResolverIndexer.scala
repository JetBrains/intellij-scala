package org.jetbrains.sbt
package resolvers

import java.io.{Closeable, File}

import com.intellij.openapi.progress.ProgressIndicator
import org.apache.maven.index.context.{IndexCreator, IndexUtils}
import org.apache.maven.index.updater.{IndexUpdateRequest, IndexUpdater, WagonHelper}
import org.apache.maven.index.{ArtifactInfo, Indexer}
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.events.TransferEvent
import org.apache.maven.wagon.observers.AbstractTransferListener
import org.codehaus.plexus.DefaultPlexusContainer

/**
 * @author Nikolay Obedin
 * @since 7/28/14.
 */
class SbtResolverIndexer private (val root: String, val indexDir: File, val cacheDir: File) extends Closeable {
  import scala.collection.JavaConversions._

  indexDir.mkdirs()
  cacheDir.mkdirs()
  if (!indexDir.exists || !indexDir.isDirectory)
    throw new RuntimeException("Can't create Maven Indexer index dir: %s" format indexDir.absolutePath)
  if (!cacheDir.exists || !cacheDir.isDirectory)
    throw new RuntimeException("Can't create Maven Indexer cache dir: %s" format cacheDir.absolutePath)

  // This lines are absolutely necessary
  // Otherwise Plexus container won't find maven-indexer interfaces' implementations
  private val origClassLoader = Thread.currentThread().getContextClassLoader
  Thread.currentThread().setContextClassLoader(classOf[Indexer].getClassLoader)

  private val container = new DefaultPlexusContainer()
  private val indexer   = container.lookup(classOf[Indexer])
  private val updater   = container.lookup(classOf[IndexUpdater])
  private val httpWagon = container.lookup(classOf[Wagon], "http")

  private val indexers = seqAsJavaList(Seq(
    container.lookup(classOf[IndexCreator], "min"),
    container.lookup(classOf[IndexCreator], "jarContent"),
    container.lookup(classOf[IndexCreator], "maven-plugin")
  ))

  private val context = indexer.createIndexingContext(
    root.shaDigest, root.shaDigest,
    cacheDir, indexDir,
    root, null, true, true, indexers
  )


  def close() {
    indexer.closeIndexingContext(context, false)
    container.dispose()
    Thread.currentThread().setContextClassLoader(origClassLoader)
  }

  def update(progressIndicator: Option[ProgressIndicator]) {
    val transferListener = new AbstractTransferListener {
      var downloadedBytes = 0
      override def transferProgress(evt: TransferEvent, bytes: Array[Byte], length: Int) =
        progressIndicator foreach (indicator => {
          downloadedBytes += length
          val done = (downloadedBytes.toFloat / evt.getResource.getContentLength) / 2.0
          indicator.setFraction(done)
        })
      override def transferStarted(evt: TransferEvent) =
        progressIndicator foreach (indicator => {
          indicator.setText2("Downloading Maven indexes")
          indicator.setFraction(0.0)
        })
    }
    val fetcher = new WagonHelper.WagonFetcher(httpWagon, transferListener, null, null)
    val updateRequest = new IndexUpdateRequest(context, fetcher)
    updater.fetchAndUpdateIndex(updateRequest)
  }

  def foreach(f: (ArtifactInfo => Unit), progressIndicator: Option[ProgressIndicator]) {
    progressIndicator foreach (_.setText2("Converting Maven indexes"))
    val searcher = context.acquireIndexSearcher()
    try {
      val reader = searcher.getIndexReader
      val maxDoc = reader.maxDoc()
      1.to(maxDoc) foreach (i => {
        val info = IndexUtils.constructArtifactInfo(reader.document(i-1), context)
        if (info != null)
          f(info)
        progressIndicator foreach (_.setFraction(0.5 + 0.5 * (i.toFloat / maxDoc)))
      })
    } finally {
      context.releaseIndexSearcher(searcher)
    }
  }
}

object SbtResolverIndexer {
  object Paths {
    val CACHE_DIR = "cache"
    val INDEX_DIR = "index"
  }

  def getInstance(root: String, indexDir: File) =
    new SbtResolverIndexer(root, indexDir / Paths.INDEX_DIR, indexDir / Paths.CACHE_DIR)
}

