package org.jetbrains.sbt.language.utils

import com.google.common.cache.{Cache, CacheBuilder}
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager}
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import spray.json._

import java.net.{HttpURLConnection, URLEncoder}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedDeque
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object CustomPackageSearchApiClient {
  private val logger = Logger.getInstance(this.getClass)

  private val baseUrl = "https://package-search.services.jetbrains.com/api"
  private val timeoutInSeconds = 10

  private val cache: Cache[String, Future[List[SbtExtendedArtifactInfo]]] = CacheBuilder
    .newBuilder()
    .concurrencyLevel(4)
    .maximumSize(10000)
    .expireAfterWrite(Duration.ofMinutes(10))
    .build[String, Future[List[SbtExtendedArtifactInfo]]]()

  private implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(AppExecutorUtil.getAppExecutorService)

  sealed trait ContentTypes
  private object ContentTypes {
    object Minimal extends ContentTypes {
      override def toString: String = "application/vnd.jetbrains.packagesearch.minimal.v2+json"
    }
  }


  private val headers = List(
    ("Scala-Plugin-Version", PluginManagerCore.getPlugins.find(_.getName == "Scala").map(_.getVersion).getOrElse("-")),
    ("IDEA-Build-Number", ApplicationInfo.getInstance().getBuild.asString)
  )


  private def handleRequest(url: String, acceptContentType: String, timeoutInSeconds: Int = 10): String = try {

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
      else if (statusCode != HttpURLConnection.HTTP_OK)
        throw new RuntimeException(s"http status code $statusCode")
      else
        throw new RuntimeException(s"Empty body response text")
    })
  } catch {
    case c: ControlFlowException => throw c
    case _: Exception =>
      ""
  }

  private def performSearch(cacheKey: String,
                            searchParams: CustomPackageSearchParams,
                            consumer: SbtExtendedArtifactInfo => Unit,
                            searchLogic: () => List[SbtExtendedArtifactInfo]
                           ): Future[List[SbtExtendedArtifactInfo]] = {
    val cacheAvailable = {
      if (searchParams.useCache) Option(cache.getIfPresent(cacheKey))
      else None
    }

    val result = cacheAvailable.getOrElse {
      val newFuture = Future {
        Option(searchLogic())
          .map(_.toList)
          .getOrElse(List.empty)

      }
      cache.put(cacheKey, newFuture)

      newFuture
    }

    result.andThen {
      case Failure(error) => logger.warn("Searching for library dependencies failed",error)
      case Success(artifactInfos) =>
        artifactInfos.foreach(consumer)
    }
  }

  private def createExactDependencyURL(groupId: String, artifactId: String): String = s"$baseUrl/package/$groupId:$artifactId"

  private def createFullTextURL(text: String): String = s"$baseUrl/package?query=${encode(text)}"

  private def extractDepFromJson(pkg: JsValue): SbtExtendedArtifactInfo = {
    pkg.asJsObject.getFields("group_id", "artifact_id", "versions") match {
      case Seq(JsString(groupId), JsString(artifactId), JsArray(versions)) =>
        SbtExtendedArtifactInfo(groupId, artifactId, versions.map(version => version.asJsObject.getFields("version").head match {
          case JsString(v) =>
            v
          case _ =>
            null
        }).filter(_ != null).distinct.toList)
      case _ => null
    }

  }

  private def searchExactDependencyFromServer(groupId: String,
                                              artifactId: String,
                                              contentType: ContentTypes = ContentTypes.Minimal): () => List[SbtExtendedArtifactInfo] = () => try {
    val reqRes = handleRequest(createExactDependencyURL(groupId, artifactId), contentType.toString, timeoutInSeconds)
    if (reqRes.nonEmpty) {
      List(extractDepFromJson(reqRes.parseJson.asJsObject.getFields("package").head)).filter(_ != null)
    }
    else Nil
  } catch {
    case c: ControlFlowException => throw c
    case e: Exception =>
      logger.warn("Problem arises when retrieving library dependency versions info from server", e)
      Nil
  }

  private def extractDepsJson(str: String): List[SbtExtendedArtifactInfo] = {
    if (str.nonEmpty) {
      str.parseJson.asJsObject.getFields("packages").head match {
        case arr: JsArray =>
          arr.elements.map(elem => extractDepFromJson(elem)).toList.filter(_ != null)
        case _ =>
          Nil
      }
    }
    else
      Nil
  }

  private def searchFullTextFromServer(text: String, contentType: ContentTypes = ContentTypes.Minimal): () => List[SbtExtendedArtifactInfo] = () => try {
    if (text.nonEmpty) {
      val reqRes = handleRequest(createFullTextURL(text), contentType.toString, timeoutInSeconds)
      extractDepsJson(reqRes)
    }
    else Nil
  } catch {
    case c: ControlFlowException => throw c
    case e: Exception =>
      logger.warn("Problem arises when retrieving library dependencies from server using full-text search", e)
      Nil

  }

  @ApiStatus.Obsolete
  def searchExactDependency(groupId: String,
                            artifactId: String,
                            searchParams: CustomPackageSearchParams,
                            contentType: ContentTypes = ContentTypes.Minimal,
                            consumer: SbtExtendedArtifactInfo => Unit): Future[List[SbtExtendedArtifactInfo]] = {
    val cacheKey = s"^$groupId:$artifactId"
    performSearch(cacheKey, searchParams, consumer, searchExactDependencyFromServer(groupId, artifactId, contentType))
  }

  @ApiStatus.Obsolete
  def searchFullText(text: String,
                     searchParams: CustomPackageSearchParams,
                     contentTypes: ContentTypes = ContentTypes.Minimal,
                     consumer: SbtExtendedArtifactInfo => Unit): Future[List[SbtExtendedArtifactInfo]] = {
    performSearch(text, searchParams, consumer, searchFullTextFromServer(text, contentTypes))
  }

  private def encode(s: String): String = URLEncoder.encode(s.trim, StandardCharsets.UTF_8)
}

case class CustomPackageSearchParams(useCache: Boolean)

object CustomPackageSearchApiHelper {
  /** Use [[org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient.searchById]] */
  @ApiStatus.Obsolete
  def searchDependencyVersions(groupId: String,
                               artifactId: String,
                               searchParams: CustomPackageSearchParams,
                               cld: ConcurrentLinkedDeque[SbtExtendedArtifactInfo]): Future[List[SbtExtendedArtifactInfo]] = {

    CustomPackageSearchApiClient.searchExactDependency(
      groupId = groupId,
      artifactId = artifactId,
      searchParams = searchParams,
      consumer = (artifact: SbtExtendedArtifactInfo) => cld.add(artifact)
    )

  }

  /** Use [[org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient.searchByQuery]] */
  @ApiStatus.Obsolete
  def searchDependency(groupId: String,
                       artifactId: String,
                       searchParams: CustomPackageSearchParams,
                       cld: ConcurrentLinkedDeque[SbtExtendedArtifactInfo],
                       fillArtifact: Boolean): Future[List[SbtExtendedArtifactInfo]] = {
    val finalGroupId = formatString(groupId)
    val finalArtifactId = formatString(artifactId)
    val text = if (finalGroupId.nonEmpty && finalArtifactId.nonEmpty) s"$finalGroupId:$finalArtifactId" else finalGroupId + finalArtifactId
    if (fillArtifact) {
      CustomPackageSearchApiClient.searchFullText(
        text = text,
        searchParams = searchParams,
        consumer = (artifact: SbtExtendedArtifactInfo) => if (finalGroupId == artifact.groupId) cld.add(artifact)
      )
    }
    else {
      CustomPackageSearchApiClient.searchFullText(
        text = text,
        searchParams = searchParams,
        consumer = (artifact: SbtExtendedArtifactInfo) => cld.add(artifact)
      )
    }

  }

  @ApiStatus.Obsolete
  def waitAndAdd(searchFuture: Future[List[SbtExtendedArtifactInfo]],
                 cld: ConcurrentLinkedDeque[SbtExtendedArtifactInfo],
                 consumer: SbtExtendedArtifactInfo => Unit):Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      consumer(SbtExtendedArtifactInfo("org.scalatest", "scalatest", List("3.0.8", "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")))
      return
    }

    while (!searchFuture.isCompleted || !cld.isEmpty) {
      ProgressManager.checkCanceled()
      val item = cld.poll()
      if (item != null)
        consumer(item)
    }

  }

  private def formatString(str: String) = str.replaceAll("^\"+|\"+$", "")
}
