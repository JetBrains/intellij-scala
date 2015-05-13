package org.jetbrains.sbt.project.template.activator

import java.io.{File, IOException}
import java.net.{URLClassLoader, HttpURLConnection}

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.util.io.{FileUtil, StreamUtil}
import com.intellij.util.io.ZipUtil
import com.intellij.util.net.HttpConfigurable
import org.apache.lucene.document.Document
import org.apache.lucene.index.{DirectoryReader, IndexReader}
import org.apache.lucene.store.FSDirectory
import org.jetbrains.plugins.scala.project.template


/**
 * User: Dmitry.Naydanov
 * Date: 20.01.15.
 *
 *
 */
class ActivatorRepoProcessor {
  import org.jetbrains.sbt.project.template.activator.ActivatorRepoProcessor._

  private var extractedHash: Option[String] = None
  private var indexFile: Option[(String, File)] = None


  private def error(msg: String, ex: Throwable = null) {
    ActivatorRepoProcessor.log.error(msg, ex)
    throw new ConfigurationException(msg)
  }

  private def errorStr(msg: String) = error(msg, null)

  private def urlString = s"$REPO_URI/$INDEX_DIR/$VERSION"

  private def extractHash(): Option[String] = {
    if (extractedHash.isEmpty) extractedHash = {
      var downloaded: Option[String] = None

      try {
        downloaded = ActivatorRepoProcessor.downloadStringFromRepo(s"$urlString/$PROPERTIES")
      } catch {
        case io: IOException => error("Can't download index", io)
      }

      downloaded flatMap {
        case str => str.split('\n').find {
          case s => s.trim startsWith CACHE_HASH
        } map {
          case hashStr => hashStr.trim.stripPrefix(CACHE_HASH)
        }
      }
    }

    extractedHash
  }

  private def downloadIndex(): Option[File] = {
    if (extractedHash.flatMap(a => indexFile.map(b => (a, b._1))).exists(a => a._1 == a._2)) indexFile.map(_._2) else {
      extractHash() flatMap {
        case hash =>
          val tmpFile = FileUtil.createTempFile(s"index-$hash", ".zip", true)
          val downloaded = ActivatorRepoProcessor.downloadFile(s"$urlString/${indexName(hash)}",
            tmpFile.getCanonicalPath, errorStr, ProgressManager.getInstance().getProgressIndicator)

          if (downloaded) {
            indexFile = Some((hash, tmpFile))
            Some(tmpFile)
          } else None
      }
    }
  }

  private def processIndex(location: File): Map[String, DocData] = {
    if (!location.exists()) return Map.empty

    var reader: IndexReader = null

    try {
      template.usingTempDirectoryWithHandler("index-activator", None)(
      {case io: IOException => error("Can't process templates list", io); Map.empty[String, ActivatorRepoProcessor.DocData]}, {case io: IOException => }) {
        extracted =>

          ZipUtil.extract(location, extracted, null)

          import org.apache.lucene
          import org.apache.lucene.search.IndexSearcher

          val loader = getClass.getClassLoader match { //hack to avoid lucene 2.4.1 from bundled maven plugin
            case urlLoader: URLClassLoader =>
              new URLClassLoader(urlLoader.getURLs, null)
            case other => other
          }
          loader.loadClass("org.apache.lucene.store.FSDirectory")

          reader = DirectoryReader.open(FSDirectory.open(extracted))
          val searcher = new IndexSearcher(reader)
          val docs = searcher.search(new lucene.search.MatchAllDocsQuery, reader.maxDoc())
          val data = docs.scoreDocs.map { case doc => reader document doc.doc }

          data.map {
            case docData => Keys.from(docData)
          }.toMap
      }
    } catch {
      case io: IOException =>
        error("Can't process templates list", io)
        Map.empty
    } finally {
      if (reader != null) reader.close()
    }
  }

  def extractRepoData(): Map[String, DocData] = downloadIndex() match {
    case None => error("No index file"); Map.empty
    case Some(file) => processIndex(file)
  }
}

object ActivatorRepoProcessor {
  val REPO_URI = "http://downloads.typesafe.com/typesafe-activator"
  private val INDEX_DIR = "index"
  private val TEMPLATES_DIR = "templates"
  private val VERSION = "v2"
  private val PROPERTIES = "current.properties"
  private val CACHE_HASH = "cache.hash="

  private def indexName(hash: String) = s"index-$hash.zip"
  private def templateFileName(id: String) = s"$id.zip"
  private def calculateHash(id: String): String = id.take(2) + "/" + id.take(6) + "/"

  private val log = Logger.getInstance(classOf[ActivatorRepoProcessor])

  case class DocData(id: String, title: String, author: String, src: String, category: String, desc: String, tags: String)

  object Keys {
    class Key(val keyName: String) {
      def getValue(doc: Document): String = doc.get(keyName)
    }

    val TEMPLATE_ID = new Key("id")
    val NAME = new Key("name")
    val TITLE = new Key("title")
    val TAGS = new Key("tags")
    val AUTHOR_NAME = new Key("authorName")
    val SOURCE_LINK = new Key("sourceLink")
    val CATEGORY = new Key("category")
    val DESCRIPTION = new Key("description")

    def from(doc: Document) =
      (NAME getValue doc, DocData(TEMPLATE_ID getValue doc, TITLE getValue doc, AUTHOR_NAME getValue doc,
        SOURCE_LINK getValue doc, CATEGORY getValue doc, DESCRIPTION getValue doc, TAGS getValue doc))
  }


  def downloadStringFromRepo(url: String): Option[String] = {
    val conf = HttpConfigurable.getInstance()
    var connection: HttpURLConnection = null

    try {
      connection = conf openHttpConnection url
      connection.connect()

      val status = connection.getResponseMessage
      if (status != null && status.trim.startsWith("OK")) {
        val text = StreamUtil.readText(connection.getInputStream, "utf-8" /*connection.getContentEncoding*/)
        Some(text)
      } else None
    } catch {
      case _: Exception => None
    } finally {
      if (connection != null) connection.disconnect()
    }
  }

  def downloadFile(url: String, toFile: String, onError: String => Unit, indicator: ProgressIndicator = null): Boolean = {
    try {
      val file = new File(toFile)
      if (!file.exists()) return false

      ActivatorDownloadUtil.downloadContentToFile(indicator, url, file)
      true
    } catch {
      case io: IOException =>
        onError(io.getMessage)
        log.error(s"Can't download $url", io)
        false
    }
  }

  def downloadTemplateFromRepo(id: String, pathTo: File, onError: String => Unit, indicator: ProgressIndicator = null) {
    try {
      val url = s"$REPO_URI/$TEMPLATES_DIR/${calculateHash(id)}${templateFileName(id)}"
      ActivatorDownloadUtil.downloadContentToFile(indicator, url, pathTo)
    } catch {
      case io: IOException =>
        log.error(s"Can't download template $id", io)
        onError(io.getMessage)
    }

  }
}
