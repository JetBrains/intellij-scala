package org.jetbrains.plugins.scala.packagesearch.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
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

  def searchByQuery(groupId: String, artifactId: String): CompletableFuture[Seq[ApiPackage]] = {
    val query =
      if (groupId.isEmpty || artifactId.isEmpty) groupId + artifactId
      else s"$groupId:$artifactId"
    byQueryCache.get(query)
  }

  def searchById(groupId: String, artifactId: String): CompletableFuture[Option[ApiPackage]] = {
    val id = s"$groupId:$artifactId"
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
}
