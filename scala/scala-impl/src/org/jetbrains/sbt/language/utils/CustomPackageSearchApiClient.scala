package org.jetbrains.sbt.language.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.progress.{ProgressIndicatorProvider, ProgressManager}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.packagesearch.intellij.plugin.api.http.EmptyBodyException
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import org.jetbrains.concurrency.{AsyncPromise, Promise}
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.sbt.language.utils.CustomPackageSearchApiClient.searchDependencyVersions

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import spray.json._

import java.net.HttpURLConnection
import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

object CustomPackageSearchApiClient {
  private val baseUrl = "https://package-search.services.jetbrains.com/api"
  private val timeoutInSeconds = 10

  private val cache = new ConcurrentHashMap[String, Future[util.Collection[MavenRepositoryArtifactInfo]]]()
  private val executorService = AppExecutorUtil.createBoundedScheduledExecutorService("CustomPackageSearchApiClient", 2)

  sealed trait ContentTypes
  private object ContentTypes {
    object Standard extends ContentTypes {
      override def toString: String = "application/vnd.jetbrains.packagesearch.standard.v2+json"
    }
    object Minimal extends ContentTypes {
      override def toString: String = "application/vnd.jetbrains.packagesearch.minimal.v2+json"
    }
  }

  private val pluginEnvironment = new PluginEnvironment()

  private val headers = List(
    ("JB-Plugin-Version", pluginEnvironment.getPluginVersion),
    ("JB-IDE-Version", pluginEnvironment.getIdeVersion))

  def foundInCache(key: String, callback: MavenRepositoryArtifactInfo => Unit): Promise[Int] = {
    val future = cache.get(key)
    if (future != null) {
      val p: AsyncPromise[Int] = new AsyncPromise()
      future.onComplete {
        case Success(artifactsList) =>
          artifactsList.asScala.foreach(callback)
          p.setResult(1)
        case Failure(e) =>
          p.setError(e)
      }
      return p
    }
    null
  }

  def handleRequest(url: String, acceptContentType: String, timeoutInSeconds: Int = 10): String = try {

    val builder = HttpRequests
      .request(url)
      .productNameAsUserAgent()
      .accept(acceptContentType)
      .connectTimeout(timeoutInSeconds * 1000)
      .readTimeout(timeoutInSeconds * 1000)
      .tuner(connection => {
        headers.foreach(header => {
          connection.setRequestProperty(header._1, header._2)
        })
      })

    builder.connect(request => {
      val statusCode = request.getConnection.asInstanceOf[HttpURLConnection].getResponseCode
      val responseText = request.readString()
      if (statusCode == HttpURLConnection.HTTP_OK && responseText.nonEmpty)
        return responseText
      else
        throw new EmptyBodyException
    })
  } catch {
    case _: Exception =>
      ""
  }

  def performSearch(cacheKey: String,
                    consumer: MavenRepositoryArtifactInfo => Unit,
                    searchLogic: () => util.Collection[MavenRepositoryArtifactInfo]
                   ): Promise[Int] = {
    val cacheValue = foundInCache(cacheKey, consumer)
    if (cacheValue != null) {
      return cacheValue
    }

    val promise = new AsyncPromise[Int]()
    val wrapper = ProgressWrapper.wrap(ProgressIndicatorProvider.getInstance().getProgressIndicator)
    val resultSet: util.Collection[MavenRepositoryArtifactInfo] = Collections.synchronizedSet(new util.LinkedHashSet())
    executorService.submit(new Runnable {
      override def run(): Unit = {
        try {
          ProgressManager.getInstance().runProcess(
            (() => {
              val collection = searchLogic()
              if (collection != null) collection.asScala.foreach(consumer)
              promise.setResult(1)
            }): Runnable,
            wrapper
          )
        } catch {
          case e: Exception =>
            promise.setError(e)
        }
      }
    })
    promise.onSuccess((_: Int) => {
      if (resultSet.asScala.nonEmpty) {
        cache.put(cacheKey, Future {resultSet})
      }
    })
  }

  def getDependencyVersionsInfoFromServer(groupId: String,
                                          artifactId: String,
                                          cacheKey: String,
                                          contentType: ContentTypes = ContentTypes.Minimal): () => util.Collection[MavenRepositoryArtifactInfo] = () => try {
    val reqRes = handleRequest(s"$baseUrl/package/$groupId:$artifactId", contentType.toString, timeoutInSeconds)
    if (reqRes.nonEmpty) {
      reqRes.parseJson.asJsObject.getFields("package") match {
        case Seq(obj: JsObject) =>
          val versions = obj.getFields("versions").head
          versions match {
            case array: JsArray =>
              val versionList: util.List[String] = array.elements.map(version => version.asJsObject.getFields("version").head match {
                case JsString(v) =>
                  v
                case _ =>
                  null
              }).filter(_ != null).distinct.toList.asJava
              List(new MavenRepositoryArtifactInfo(groupId, artifactId, versionList)).asJavaCollection
            case _ =>
              null
          }
        case _ =>
          null
      }
    }
    else
      null
  } catch {
    case _: Exception =>
      null
  }

  def searchDependencyVersions(groupId: String,
                            artifactId: String,
                            contentType: ContentTypes = ContentTypes.Minimal,
                            consumer: MavenRepositoryArtifactInfo => Unit): Promise[Int] = {
    val cacheKey = s"$groupId:$artifactId"
    performSearch(cacheKey, consumer, getDependencyVersionsInfoFromServer(groupId, artifactId, cacheKey, contentType))
  }
}

object CustomPackageSearchApiHelper {
  def waitAndAddDependencyVersions(groupId: String,
                                   artifactId: String,
                                   consumer: MavenRepositoryArtifactInfo => Unit): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      consumer(new MavenRepositoryArtifactInfo("org.scalatest", "scalatest", util.Arrays.asList("3.0.8", "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")))
      return
    }

    val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
    val callback: MavenRepositoryArtifactInfo => Unit = (artifact: MavenRepositoryArtifactInfo) => cld.add(artifact)
    val promise = searchDependencyVersions(groupId = groupId, artifactId = artifactId, consumer = callback)

    while (promise.getState == Promise.State.PENDING || !cld.isEmpty) {
      ProgressManager.checkCanceled()
      val item = cld.poll()
      if (item != null)
        consumer(item)
    }
  }
}
