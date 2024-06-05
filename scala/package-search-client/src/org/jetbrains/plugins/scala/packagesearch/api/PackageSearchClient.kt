package org.jetbrains.plugins.scala.packagesearch.api

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.util.containers.orNull
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints
import org.jetbrains.packagesearch.api.v3.http.searchPackages
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

@Service(Level.APP)
class PackageSearchClient(private val cs: CoroutineScope) {
    companion object {
        @JvmStatic
        fun instance(): PackageSearchClient = service()
    }

    private val apiClient = PackageSearchApiClient(
        endpoints = PackageSearchEndpoints.PROD,
        httpClient = PackageSearchApiClient.defaultHttpClient {
            install(HttpTimeout) {
                // default timeout might be too high considering blocking operations in completion providers
                requestTimeoutMillis = 4.seconds.inWholeMilliseconds
            }
            install(UserAgent) {
                agent = userAgent()
            }
        }
    )

    private val byIdCache: AsyncExpirableCache<String, Optional<ApiPackage>> =
        AsyncExpirableCache(cs) { Optional.ofNullable(searchById(it)) }

    private val byQueryCache: AsyncExpirableCache<String, List<ApiPackage>> =
        AsyncExpirableCache(cs) { searchByQuery(it) }

    private suspend fun searchById(id: String): ApiPackage? =
        if (ApplicationManager.getApplication()?.isUnitTestMode != false) null
        else apiClient.getPackageInfoByIds(setOf(id))[id]

    private suspend fun searchByQuery(query: String): List<ApiPackage> =
        if (ApplicationManager.getApplication()?.isUnitTestMode != false) emptyList()
        else apiClient.searchPackages { searchQuery = query }

    fun searchByQuery(
        groupId: String,
        artifactId: String,
        useCache: Boolean = true,
    ): CompletableFuture<List<ApiPackage>> {
        val query = queryCacheKey(groupId, artifactId)
        return if (useCache) byQueryCache.get(query) else cs.future { searchByQuery(query) }
    }

    fun searchById(groupId: String, artifactId: String): CompletableFuture<ApiPackage?> {
        val id = idCacheKey(groupId, artifactId)
        return byIdCache.get(id).thenApply { it.orNull() }
    }

    private fun queryCacheKey(groupId: String, artifactId: String): String =
        if (groupId.isEmpty() || artifactId.isEmpty()) groupId + artifactId
        else "$groupId:$artifactId"

    private fun idCacheKey(groupId: String, artifactId: String) = "maven:$groupId:$artifactId"

    private fun userAgent(): String = ApplicationManager.getApplication()?.takeIf { !it.isDisposed }?.let {
        val productName = ApplicationNamesInfo.getInstance().fullProductName
        val version = ApplicationInfo.getInstance().build.asStringWithoutProductCode()
        "$productName/$version"
    } ?: "IntelliJ"

    @ApiStatus.Internal
    @VisibleForTesting
    fun updateByQueryCache(groupId: String, artifactId: String, result: List<ApiPackage>) {
        val key = queryCacheKey(groupId, artifactId)
        val value = CompletableFuture.completedFuture(result)
        byQueryCache.updateCache(key, value)
    }

    @ApiStatus.Internal
    @VisibleForTesting
    fun updateByIdCache(groupId: String, artifactId: String, result: ApiPackage?) {
        val key = idCacheKey(groupId, artifactId)
        val value = CompletableFuture.completedFuture(Optional.ofNullable(result))
        byIdCache.updateCache(key, value)
    }
}
