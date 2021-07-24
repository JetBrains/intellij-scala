package org.jetbrains.sbt.language.utils

import com.google.common.cache.{Cache, CacheBuilder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.api.http.EmptyBodyException
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

  private def createSuggestPrefixURL(groupId: String, artifactId: String): String = {
    val groupParam = if (StringUtil.isEmpty(groupId)) "" else s"groupId=${encode(groupId.trim)}"
    val artifactParam = if (StringUtil.isEmpty(artifactId)) "" else s"artifactId=${encode(artifactId.trim)}"
    val text = if (groupParam.nonEmpty && artifactParam.nonEmpty) s"$groupParam&$artifactParam" else s"$groupParam$artifactParam"
    s"$baseUrl/package$text"
  }

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

  def searchExactDependencyFromServer(groupId: String,
                                      artifactId: String,
                                      contentType: ContentTypes = ContentTypes.Minimal): () => List[SbtExtendedArtifactInfo] = () => try {
    val reqRes = handleRequest(createExactDependencyURL(groupId, artifactId), contentType.toString, timeoutInSeconds)
    if (reqRes.nonEmpty) {
      List(extractDepFromJson(reqRes.parseJson.asJsObject.getFields("package").head)).filter(_ != null)
    }
    else Nil
  } catch {
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

  def searchFullTextFromServer(text: String, contentType: ContentTypes = ContentTypes.Minimal): () => List[SbtExtendedArtifactInfo] = () => try {
    if (text.nonEmpty) {
      val reqRes = handleRequest(createFullTextURL(text), contentType.toString, timeoutInSeconds)
      extractDepsJson(reqRes)
    }
    else Nil
  } catch {
    case e: Exception =>
      logger.warn("Problem arises when retrieving library dependencies from server using full-text search", e)
      Nil

  }

  def searchPrefixFromServer(groupId: String,
                             artifactId: String,
                             contentTypes: ContentTypes = ContentTypes.Minimal
                            ): () => List[SbtExtendedArtifactInfo] = () => try {
    if (groupId.isEmpty) {
      val reqRes = handleRequest(createSuggestPrefixURL(groupId, artifactId), contentTypes.toString, timeoutInSeconds)
      extractDepsJson(reqRes)

    }
    else Nil

  } catch {
    case e: Exception =>
      logger.warn("Problem arises when retrieving library dependencies from server using prefix search", e)
      Nil

  }

  def searchExactDependency(groupId: String,
                            artifactId: String,
                            searchParams: CustomPackageSearchParams,
                            contentType: ContentTypes = ContentTypes.Minimal,
                            consumer: SbtExtendedArtifactInfo => Unit): Future[List[SbtExtendedArtifactInfo]] = {
    val cacheKey = s"$groupId:$artifactId"
    performSearch(cacheKey, searchParams, consumer, searchExactDependencyFromServer(groupId, artifactId, contentType))
  }

  def searchFullText(text: String,
                     searchParams: CustomPackageSearchParams,
                     contentTypes: ContentTypes = ContentTypes.Minimal,
                     consumer: SbtExtendedArtifactInfo => Unit): Future[List[SbtExtendedArtifactInfo]] = {
    performSearch(text, searchParams, consumer, searchFullTextFromServer(text, contentTypes))
  }

  def searchPrefix(groupId: String,
                   artifactId: String,
                   searchParams: CustomPackageSearchParams,
                   contentTypes: ContentTypes = ContentTypes.Minimal,
                   consumer: SbtExtendedArtifactInfo => Unit): Future[List[SbtExtendedArtifactInfo]] = {
    val cacheKey = s"$groupId:$artifactId"
    performSearch(cacheKey, searchParams, consumer, searchPrefixFromServer(groupId, artifactId, contentTypes))
  }

  private def encode(s: String): String = URLEncoder.encode(s.trim, StandardCharsets.UTF_8)
}

case class CustomPackageSearchParams(useCache: Boolean)

object CustomPackageSearchApiHelper {
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

  def searchDependency(groupId: String,
                       artifactId: String,
                       searchParams: CustomPackageSearchParams,
                       cld: ConcurrentLinkedDeque[SbtExtendedArtifactInfo],
                       fillArtifact: Boolean): Future[List[SbtExtendedArtifactInfo]] = {
    val finalGroupId = formatString(groupId)
    val finalArtifactId = formatString(artifactId)
    val text = if (finalGroupId.nonEmpty && finalArtifactId.nonEmpty) s"$finalGroupId:$finalArtifactId" else finalGroupId + finalArtifactId
    if (fillArtifact) {
      CustomPackageSearchApiClient.searchPrefix(
        groupId,
        artifactId,
        searchParams,
        consumer = (artifact: SbtExtendedArtifactInfo) => cld.add(artifact)
      )
    }
    CustomPackageSearchApiClient.searchFullText(
      text = text,
      searchParams = searchParams,
      consumer = (artifact: SbtExtendedArtifactInfo) => cld.add(artifact)
    )
  }

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
