package org.jetbrains.sbt.project.template.activator

import java.io.{File, IOException}
import java.net.URLClassLoader

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import com.intellij.util.io.ZipUtil
import org.apache.lucene.index.{DirectoryReader, IndexReader}
import org.apache.lucene.store.FSDirectory
import org.jetbrains.plugins.scala.project.template

/**
  * User: Dmitry.Naydanov
  * Date: 20.01.16.
  */
class ActivatorCachedRepoProcessor extends ProjectComponent {
  import ActivatorCachedRepoProcessor._
  import ActivatorRepoProcessor._
  
  private def getCacheDataPath = new File(PathManager.getSystemPath, "activator_cache") //todo to-do-to-do

  private var extractedHash: Option[String] = None
  private var indexFile: Option[(String, File)] = None
  private var workOffline = false


  private def error(msg: String, ex: Throwable = null) {
    ActivatorCachedRepoProcessor.logError(msg, ex)
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

  private def cacheFile(file: File, where: File) {
    val cacheDir = getCacheDataPath
    
    if (cacheDir.exists() || cacheDir.mkdir()) {
      if (where.exists() || where.createNewFile()) FileUtil.copy(file, where)
    }
  }
  
  private def processIndex(location: File): Map[String, DocData] = {
    if (!location.exists()) return Map.empty

    var reader: IndexReader = null

    try {
      template.usingTempDirectoryWithHandler("index-activator", None)(
        {case io: IOException => error("Can't process templates list", io); Map.empty[String, ActivatorRepoProcessor.DocData]}, {case _: IOException => }) {
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
  
  private def getOrDownloadTemplate(templateId: String, pathTo: File, onError: String => Unit) {
    var hasError = false
    
    val fileName = ActivatorRepoProcessor.templateFileName(templateId)
    val cachedTemplate = new File(getCacheDataPath, fileName)
    
    val myOnError = (a: String) => {
      
      
      hasError = true
      
      if (!cachedTemplate.exists()) {
        onError(a)
        return 
      }
      
      try {
        FileUtil.copy(cachedTemplate, pathTo)
      } catch {
        case _: IOException => onError(a)
      }
    }
    
    ActivatorRepoProcessor.downloadTemplateFromRepo(templateId, pathTo, myOnError)
    workOffline = hasError
    if (!workOffline) cacheFile(pathTo, cachedTemplate)
  }
  
  def createTemplate(templateId: String, extractTo: File, onError: String => Unit) {
    val contentDir = FileUtilRt.createTempDirectory(s"$templateId-template-content", "", true)
    val contentFile =  new File(contentDir, "content.zip")

    contentFile.createNewFile()

    getOrDownloadTemplate(templateId, contentFile, onError)
    
    ZipUtil.extract(contentFile, extractTo, null)
  }

  def extractRepoData(): Map[String, DocData] = {
    val toProcess: File = downloadIndex() match {
      case Some(file) =>
        cacheFile(file, new File(getCacheDataPath, INDEX_CACHE_NAME))
        workOffline = false
        file
      case None =>
        val cacheFile = new File(getCacheDataPath, INDEX_CACHE_NAME)
        workOffline = true
        if (!cacheFile.exists()) null else cacheFile
    }

    if (toProcess != null) processIndex(toProcess) else {
      error("No index file")
      Map.empty
    }
  }
  
  override def projectClosed() {
    
  }

  override def projectOpened() {
    
  }

  override def initComponent() {
    
  }

  override def disposeComponent() {
    
  }

  override def getComponentName: String = "ScalaActivatorTemplateCache"
}

object ActivatorCachedRepoProcessor {
  private val INDEX_CACHE_NAME = "activator_template_index"
  private val log = Logger.getInstance(classOf[ActivatorCachedRepoProcessor])
  
  def logError(msg: String) {
    log.error(msg)
  }
  
  def logError(msg: String, ex: Throwable) {
    val newMsg = if (ex == null) msg else s"$msg : ${ex.getMessage} :\n ${ex.getStackTrace.mkString("\n")} "
    logError(newMsg)
  }
}
