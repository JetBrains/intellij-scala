package org.jetbrains.plugins.scala.packagesearch.api

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import org.jetbrains.packagesearch.api.v3.http.PackageSearchEndpoints
import org.jetbrains.packagesearch.api.v3.http.searchPackages
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

    private val byQueryCache: AsyncExpirableCache<String, List<ApiPackage>> =
        AsyncExpirableCache(cs) { searchByQuery(it) }

    private suspend fun searchByQuery(query: String): List<ApiPackage> =
        runCatching(defaultValue = emptyList()) {
            apiClient.searchPackages { searchQuery = query }
        }

    private suspend fun <T> runCatching(defaultValue: T, block: suspend () -> T): T =
        try {
            if (ApplicationManager.getApplication()?.isUnitTestMode != false) defaultValue
            else block()
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e
            defaultValue
        }

    fun searchByQuery(
        groupId: String,
        artifactId: String,
        useCache: Boolean = true,
    ): CompletableFuture<List<ApiPackage>> {
        val query = queryCacheKey(groupId, artifactId)
        return if (useCache) byQueryCache.get(query) else cs.future { searchByQuery(query) }
    }

    private fun queryCacheKey(groupId: String, artifactId: String): String =
        if (groupId.isEmpty() || artifactId.isEmpty()) groupId + artifactId
        else "$groupId:$artifactId"

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
}
