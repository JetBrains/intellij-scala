package org.jetbrains.plugins.scala.packagesearch.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scala.packagesearch.util.AsyncExpirableCache
import spray.json.DefaultJsonProtocol.immSeqFormat
import spray.json.JsonParser.ParsingException
import spray.json.{JsonReader, enrichString}

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CompletableFuture, ExecutorService}

object PackageSearchApiClient {
  private[this] val executor: ExecutorService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Scala Package Search Client background executor", 4)

  private[this] val byQueryCache: AsyncExpirableCache[String, Seq[ApiPackage]] =
    new AsyncExpirableCache(executor, searchByQuery)

  private[this] val byIdCache: AsyncExpirableCache[String, Option[ApiPackage]] =
    new AsyncExpirableCache(executor, searchById)

  def searchByQuery(groupId: String, artifactId: String, useCache: Boolean = true): CompletableFuture[Seq[ApiPackage]] = {
    val query = queryCacheKey(groupId, artifactId)

    if (useCache) byQueryCache.get(query)
    else CompletableFuture.supplyAsync(() => searchByQuery(query), executor)
  }

  def searchById(groupId: String, artifactId: String): CompletableFuture[Option[ApiPackage]] = {
    val id = idCacheKey(groupId, artifactId)
    byIdCache.get(id)
  }

  private def searchByQuery(query: String): Seq[ApiPackage] =
    search[Seq[ApiPackage]](querySearchUrl(query), "packages", Seq.empty)

  private def searchById(id: String): Option[ApiPackage] =
    search[ApiPackage](idSearchUrl(id), "package", null).toOption

  private def search[T: JsonReader](url: String, field: String, defaultValue: => T): T =
    try request(url)
      .parseJson
      .asJsObject
      .fields(field)
      .convertTo[T]
    catch {
      case e@(_: ParsingException | _: NoSuchElementException | _: IOException) =>
        Log.debug(e)
        defaultValue
    }

  @throws[IOException]
  private[this] def request(url: String): String =
    HttpRequests
      .request(url)
      .productNameAsUserAgent()
      .accept(ContentType)
      .readString()

  private val Log = Logger.getInstance(getClass)

  // Accept v2 Package Search API responses with minimized payload
  private val ContentType = "application/vnd.jetbrains.packagesearch.minimal.v2+json"

  private def encode(s: String): String = URLEncoder.encode(s.trim, StandardCharsets.UTF_8)

  private val baseUrl = "https://package-search.services.jetbrains.com/api"

  private def querySearchUrl(query: String): String = s"$baseUrl/package?query=${encode(query)}"

  private def idSearchUrl(id: String): String = s"$baseUrl/package/$id"

  private def queryCacheKey(groupId: String, artifactId: String): String =
    if (groupId.isEmpty || artifactId.isEmpty) groupId + artifactId
    else s"$groupId:$artifactId"

  private def idCacheKey(groupId: String, artifactId: String): String = s"$groupId:$artifactId"

  @Internal
  @VisibleForTesting
  def updateByQueryCache(groupId: String, artifactId: String, result: Seq[ApiPackage]): Unit = {
    val key = queryCacheKey(groupId, artifactId)
    val value = CompletableFuture.completedFuture(result)
    byQueryCache.updateCache(key, value)
  }

  @Internal
  @VisibleForTesting
  def updateByIdCache(groupId: String, artifactId: String, result: Option[ApiPackage]): Unit = {
    val key = idCacheKey(groupId, artifactId)
    val value = CompletableFuture.completedFuture(result)
    byIdCache.updateCache(key, value)
  }
}
