package org.jetbrains.sbt.project.template.activator

import java.io.{File, IOException}
import java.net.HttpURLConnection

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.net.HttpConfigurable
import org.apache.lucene.document.Document


/**
 * User: Dmitry.Naydanov
 * Date: 20.01.15.
 *
 *
 */
object ActivatorRepoProcessor {
  val REPO_URI = "http://downloads.typesafe.com/typesafe-activator"
  val INDEX_DIR = "index"
  val TEMPLATES_DIR = "templates"
  val VERSION = "v2"
  val PROPERTIES = "current.properties"
  val CACHE_HASH = "cache.hash="

  def indexName(hash: String) = s"index-$hash.zip"
  def templateFileName(id: String) = s"$id.zip"
  def calculateHash(id: String): String = id.take(2) + "/" + id.take(6) + "/"

  case class DocData(id: String, title: String, author: String, src: String, category: String, desc: String, tags: String) {
    override def toString: String = title
  }

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

    def from(doc: Document): (String, DocData) =
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
        ActivatorCachedRepoProcessor.logError(s"Can't download $url", io)
        false
    }
  }

  def downloadTemplateFromRepo(id: String, pathTo: File, onError: String => Unit, indicator: ProgressIndicator = null) {
    try {
      val url = s"$REPO_URI/$TEMPLATES_DIR/${calculateHash(id)}${templateFileName(id)}"
      ActivatorDownloadUtil.downloadContentToFile(indicator, url, pathTo)
    } catch {
      case io: IOException =>
//        log.error(s"Can't download template $id", io) - it is not an error anymore
        onError(io.getMessage)
    }

  }
}
