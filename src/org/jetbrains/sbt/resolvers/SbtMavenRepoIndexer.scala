package org.jetbrains.sbt
package resolvers

import java.io.{Closeable, File}

import com.intellij.openapi.progress.ProgressIndicator
import org.apache.maven.index._
import org.apache.maven.index.artifact.DefaultArtifactPackagingMapper
import org.apache.maven.index.context.{IndexCreator, IndexUtils, IndexingContext}
import org.apache.maven.index.incremental.DefaultIncrementalHandler
import org.apache.maven.index.updater.{DefaultIndexUpdater, IndexUpdateRequest, WagonHelper}
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.events.TransferEvent
import org.apache.maven.wagon.observers.AbstractTransferListener
import org.codehaus.plexus.DefaultPlexusContainer

/**
 * @author Nikolay Obedin
 * @since 7/28/14.
 */
class SbtMavenRepoIndexer private (val root: String, val indexDir: File) extends Closeable {
  import scala.collection.JavaConverters._

  indexDir.mkdirs()
  if (!indexDir.exists || !indexDir.isDirectory)
    throw new RuntimeException(SbtBundle("sbt.resolverIndexer.cantCreateMavenIndexDir", indexDir.absolutePath))

  // This lines are absolutely necessary
  // Otherwise Plexus container won't find maven-indexer interfaces' implementations
  private val origClassLoader = Thread.currentThread().getContextClassLoader
  Thread.currentThread().setContextClassLoader(classOf[Indexer].getClassLoader)

  private val container = new DefaultPlexusContainer()

  private val indexerEngine = new DefaultIndexerEngine
  private val queryCreator = new DefaultQueryCreator
  private val indexer   = new DefaultIndexer(new DefaultSearchEngine, indexerEngine, queryCreator)
  private val updater   = new DefaultIndexUpdater(new DefaultIncrementalHandler, java.util.Collections.emptyList())
  private val httpWagon = container.lookup(classOf[Wagon], "http")

  private val indexers = Seq(
    container.lookup(classOf[IndexCreator], "min"),
    container.lookup(classOf[IndexCreator], "jarContent"),
    container.lookup(classOf[IndexCreator], "maven-plugin")
  ).asJava

  private var context = {
    val repoUrl = if (root.startsWith("file:")) null else root
    val repoDir = if (root.startsWith("file:")) new File(root.substring(5)) else null
    indexer.createIndexingContext(
      root.shaDigest, root.shaDigest,
      repoDir, indexDir,
      repoUrl, null, true, true, indexers
    )
  }


  def close() {
    indexer.closeIndexingContext(context, false)
    container.dispose()
    Thread.currentThread().setContextClassLoader(origClassLoader)
  }

  def update(progressIndicator: Option[ProgressIndicator]) {
    if (context.getRepositoryUrl == null)
      updateLocal(progressIndicator)
    else
      updateRemote(progressIndicator)
  }

  private def updateLocal(progressIndicator: Option[ProgressIndicator]) {
    val scannerListener = new ArtifactScanningListener {
      override def scanningStarted(p1: IndexingContext) = progressIndicator foreach { indicator =>
        indicator.setText2(SbtBundle("sbt.resolverIndexer.progress.scanning"))
        indicator.setFraction(0.0)
      }
      override def scanningFinished(p1: IndexingContext, p2: ScanningResult) =
        progressIndicator foreach { _.setFraction(0.5) }
      override def artifactError(p1: ArtifactContext, p2: Exception) {}
      override def artifactDiscovered(p1: ArtifactContext): Unit =
        progressIndicator foreach { _.checkCanceled() }
    }

    val repoDir = context.getRepository
    indexer.closeIndexingContext(context, false)

      // TODO: when guys from maven-indexer fix their code (or at least Scanner class will work as it should)
      val nexusIndexer = new DefaultNexusIndexer(
        indexer,
        new DefaultScanner(new DefaultArtifactContextProducer(new DefaultArtifactPackagingMapper)),
        indexerEngine, queryCreator
      )
      val nexusContext = nexusIndexer.addIndexingContext(root.shaDigest, root.shaDigest, context.getRepository, indexDir, null, null, indexers)
      nexusIndexer.scan(nexusContext, scannerListener, true)
      nexusIndexer.removeIndexingContext(nexusContext, false)

    context = indexer.createIndexingContext(
      root.shaDigest, root.shaDigest,
      repoDir, indexDir,
      null, null, true, true, indexers
    )
  }

  private def updateRemote(progressIndicator: Option[ProgressIndicator]) {
    val transferListener = new AbstractTransferListener {
      var downloadedBytes = 0
      override def transferProgress(evt: TransferEvent, bytes: Array[Byte], length: Int) =
        progressIndicator foreach { indicator =>
          downloadedBytes += length
          val done = (downloadedBytes.toFloat / evt.getResource.getContentLength) / 2.0
          indicator.setFraction(done)
        }
      override def transferStarted(evt: TransferEvent) =
        progressIndicator foreach { indicator =>
          indicator.setText2(SbtBundle("sbt.resolverIndexer.progress.downloading"))
          indicator.setFraction(0.0)
        }
    }
    val fetcher = new WagonHelper.WagonFetcher(httpWagon, transferListener, null, null)
    val updateRequest = new IndexUpdateRequest(context, fetcher)
    updater.fetchAndUpdateIndex(updateRequest)
  }

  def foreach(f: (ArtifactInfo => Unit), progressIndicator: Option[ProgressIndicator]) {
    progressIndicator foreach (_.setText2(SbtBundle("sbt.resolverIndexer.progress.converting")))
    val searcher = context.acquireIndexSearcher()
    try {
      val reader = searcher.getIndexReader
      val maxDoc = reader.maxDoc()
      1.to(maxDoc) foreach { i =>
        progressIndicator foreach { _.checkCanceled() }
        val info = IndexUtils.constructArtifactInfo(reader.document(i-1), context)
        if (info != null)
          f(info)
        progressIndicator foreach (_.setFraction(0.5 + 0.5 * (i.toFloat / maxDoc)))
      }
    } finally {
      context.releaseIndexSearcher(searcher)
    }
  }
}

object SbtMavenRepoIndexer {
  object Paths {
    val INDEX_DIR = "index"
  }

  def apply(root: String, indexDir: File) =
    new SbtMavenRepoIndexer(root, indexDir / Paths.INDEX_DIR)
}

