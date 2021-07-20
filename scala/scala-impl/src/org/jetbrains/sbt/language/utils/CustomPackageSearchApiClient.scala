package org.jetbrains.sbt.language.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.http.EmptyBodyException
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import spray.json._

import java.net.HttpURLConnection
import java.util
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedDeque}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object CustomPackageSearchApiClient {
  private val logger = Logger.getInstance(this.getClass)

  private val baseUrl = "https://package-search.services.jetbrains.com/api"
  private val timeoutInSeconds = 10

  private val cache = new ConcurrentHashMap[String, Future[List[MavenRepositoryArtifactInfo]]]()
  private implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(AppExecutorUtil.getAppExecutorService)

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
                    searchParams: CustomPackageSearchParams,
                    consumer: MavenRepositoryArtifactInfo => Unit,
                    searchLogic: () => util.Collection[MavenRepositoryArtifactInfo]
                   ): Future[List[MavenRepositoryArtifactInfo]] = {
    val cacheAvailable =
      if (searchParams.useCache) Option(cache.get(cacheKey))
      else None

    val result = cacheAvailable.getOrElse {
      val newFuture = Future {
        Option(searchLogic())
          .map(_.asScala.toList)
          .getOrElse(List.empty)

      }
      cache.put(cacheKey, newFuture)

      newFuture
    }

    result.andThen {
      case Failure(error) => logger.warn("CustomPackageSearchApiClient.performSearch",error)
      case Success(artifactInfos) =>
        artifactInfos.foreach(consumer)
    }
  }

  def getDependencyVersionsInfoFromServer(groupId: String,
                                          artifactId: String,
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
                               searchParams: CustomPackageSearchParams,
                               contentType: ContentTypes = ContentTypes.Minimal,
                               consumer: MavenRepositoryArtifactInfo => Unit): Future[List[MavenRepositoryArtifactInfo]] = {
    val cacheKey = s"$groupId:$artifactId"
    performSearch(cacheKey, searchParams, consumer, getDependencyVersionsInfoFromServer(groupId, artifactId, contentType))
  }
}

case class CustomPackageSearchParams(useCache: Boolean)

object CustomPackageSearchApiHelper {
  def waitAndAddDependencyVersions(groupId: String,
                                   artifactId: String,
                                   searchParams: CustomPackageSearchParams,
                                   consumer: MavenRepositoryArtifactInfo => Unit): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      consumer(new MavenRepositoryArtifactInfo("org.scalatest", "scalatest", util.Arrays.asList("3.0.8", "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")))
      return
    }

    val cld: ConcurrentLinkedDeque[MavenRepositoryArtifactInfo] = new ConcurrentLinkedDeque[MavenRepositoryArtifactInfo]()
    val callback: MavenRepositoryArtifactInfo => Unit = (artifact: MavenRepositoryArtifactInfo) => cld.add(artifact)
    val future = CustomPackageSearchApiClient.searchDependencyVersions(groupId = groupId, artifactId = artifactId, searchParams = searchParams, consumer = callback)

    while (!future.isCompleted || !cld.isEmpty) {
      ProgressManager.checkCanceled()
      val item = cld.poll()
      if (item != null)
        consumer(item)
    }
  }
}
