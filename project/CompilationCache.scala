import sbt.*
import sbt.KeyRanks.Invisible
import sbt.Keys.*
import sbt.internal.inc.HashUtil

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

object CompilationCache {
  // Defined once in the `Global` scope in `community/build.sbt`, which is loaded both by the standalone community
  // build and by the ultimate build, so all modules share one cache per sbt session.
  lazy val farmHashCache: SettingKey[ConcurrentHashMap[Path, String]] =
    settingKey("Global cache of farm hash values for external dependencies").withRank(Invisible)

  private def farmHash(cache: ConcurrentHashMap[Path, String])(path: Path): String =
    cache.computeIfAbsent(path, HashUtil.farmHash(_).toString)

  lazy val pushRemoteCacheIfNeeded: TaskKey[Unit] = taskKey[Unit](
    "Push the compilation cache to the remote repository, unless the configuration has no sources and no resources, " +
      "or the repository already contains an artifact for the current remoteCacheId"
  ).withRank(Invisible)

  lazy val pullRemoteCacheIfNeeded: TaskKey[Unit] = taskKey[Unit](
    "Pull the compilation cache from the remote repository, unless the configuration has no sources and no resources"
  ).withRank(Invisible)

  // Generated sources/resources are part of the cache artifact, so they count as cacheable inputs. Only run the
  // generators (`managedSources`/`managedResources`) when there are no unmanaged inputs, which is the cheap and
  // common case.
  private def hasNoCacheableInputs: Def.Initialize[Task[Boolean]] = Def.taskDyn {
    if (unmanagedSources.value.nonEmpty || unmanagedResources.value.nonEmpty)
      Def.task(false)
    else
      Def.task(managedSources.value.isEmpty && managedResources.value.isEmpty)
  }

  // The URL of the remote cache artifact for the current configuration and remoteCacheId, mirroring the coordinates
  // used by sbt's own `pushRemoteCache`/`pullRemoteCache` (see `RemoteCache.pullRemoteCache`): maven-style layout,
  // revision `0.0.0-<remoteCacheId>`, classifier `cached-compile`/`cached-test`. The cross-version suffix must be
  // derived via `CrossVersion` because some modules set `crossPaths := false`.
  private def remoteCacheArtifactUrl: Def.Initialize[Task[Option[String]]] = Def.task {
    val projectId = remoteCacheProjectId.value
    val crossName = CrossVersion(projectId, scalaModuleInfo.value).getOrElse(identity[String] _)
    val cacheArtifact = (packageCache / artifact).value
    pushRemoteCacheTo.value.collect { case repository: MavenRepository =>
      val name = crossName(cacheArtifact.name)
      val classifierSuffix = cacheArtifact.classifier.fold("")(c => s"-$c")
      val organizationPath = projectId.organization.replace('.', '/')
      s"${repository.root.stripSuffix("/")}/$organizationPath/$name/${projectId.revision}/" +
        s"$name-${projectId.revision}$classifierSuffix.${cacheArtifact.extension}"
    }
  }

  private val requestTimeout = 30.seconds

  // The client is immutable and thread-safe; sharing one instance lets the HEAD requests issued for all modules
  // by `pushCompilationCacheAll` reuse pooled connections to the repository host.
  private lazy val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofMillis(requestTimeout.toMillis))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  private def remoteArtifactExists(url: String, allCredentials: Seq[Credentials], log: Logger): Boolean =
    try {
      val uri = new URI(url)
      val requestBuilder = HttpRequest.newBuilder(uri)
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .timeout(java.time.Duration.ofMillis(requestTimeout.toMillis))
      Credentials.forHost(allCredentials, uri.getHost).foreach { credentials =>
        val token = Base64.getEncoder.encodeToString(
          s"${credentials.userName}:${credentials.passwd}".getBytes(StandardCharsets.UTF_8)
        )
        requestBuilder.header("Authorization", s"Basic $token")
      }
      val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
      response.statusCode() match {
        case 200 => true
        case 404 => false
        case code =>
          log.warn(s"Unexpected HTTP status $code while checking for an existing compilation cache artifact at $url")
          false // fail open: attempt the push
      }
    } catch {
      case NonFatal(e) =>
        log.warn(s"Could not check for an existing compilation cache artifact at $url: ${e.getMessage}")
        false // fail open: attempt the push
    }

  private val perConfigSettings: Seq[Setting[?]] = Seq(
    // Overwrite on actual pushes, so that an interrupted upload is repaired the next time the artifact is pushed.
    pushRemoteCacheConfiguration ~= { _.withOverwrite(true) },
    // The remote cache id must change whenever anything that affects the compilation output changes: the module's
    // sources and the *content* of its dependency jars. Content matters because nightly IntelliJ builds can change
    // jar content while keeping the same version string, which used to produce builds with stale caches
    // (see #SCL-23117).
    //
    // sbt's default `remoteCacheId` is supposed to provide exactly that: it combines content hashes of the
    // unmanaged sources, content hashes of every `externalDependencyClasspath` entry, and `extraIncOptions`
    // (see `RemoteCache.configCacheSettings`). However, the classpath component is silently lost: Zinc stamps
    // binaries with `FarmHash` stamps, and `sbt.nio.FileStamp.apply` only converts `Hash`/`LastModified` stamps,
    // dropping everything else. The classpath hashing work is still performed in full, only its result is
    // discarded. The default id therefore reduces to hash(sources + extraIncOptions) and is NOT sensitive to
    // dependency changes.
    //
    // The override does not call `remoteCacheId.value` to reuse the default's working parts either: evaluating it
    // forces the whole upstream task graph, including `externalDependencyClasspath / outputFileStamps`, which is
    // the discarded content-hashing pass over the entire IntelliJ SDK and all plugin jars. The default id cannot
    // be obtained without paying for it. Instead, the id is computed self-contained from the same inputs the
    // default intends to use: source content hashes, classpath content hashes (via the global `farmHashCache`, so
    // each unique jar is hashed once per sbt session), and `extraIncOptions`. Same sensitivity, one hashing pass
    // instead of two.
    remoteCacheId := {
      // Sources are not cached in `farmHashCache`: they can change within an sbt session. Dependency jars cannot.
      val sourceHashes = unmanagedSources.value.map(_.toPath).filter(Files.exists(_))
        .map(HashUtil.farmHash(_).toString)
      val cache = (Global / farmHashCache).value
      val classpathHashes = externalDependencyClasspath.value.map(_.data.toPath).filter(Files.exists(_))
        .map(farmHash(cache))
      val extraInc = extraIncOptions.value.flatMap { case (k, v) => Vector(k, v) }
      // Sort the combined hashes, not the paths, so that the id does not depend on machine-specific path prefixes.
      val combined = (sourceHashes ++ classpathHashes ++ extraInc).sorted.mkString
      java.lang.Long.toHexString(HashUtil.farmHash(combined.getBytes(StandardCharsets.UTF_8)))
    },
    pushRemoteCache := {
      val s = streams.value
      pushRemoteCache.result.value match {
        case Value(_) => ()
        case Inc(cause) =>
          s.log.warn(s"Failed to push the compilation cache to the remote repository, continuing: $cause")
          ()
      }
    },
    pullRemoteCache := {
      val s = streams.value
      pullRemoteCache.result.value match {
        case Value(_) => ()
        case Inc(cause) =>
          s.log.warn(s"Failed to pull the compilation cache from the remote repository, continuing without it: $cause")
          ()
      }
    },
    pushRemoteCacheIfNeeded := Def.taskDyn {
      val log = streams.value.log
      val scopeName = s"${name.value} / ${configuration.value.id}"
      if (hasNoCacheableInputs.value)
        Def.task(log.debug(s"$scopeName: no sources or resources, skipping the compilation cache push"))
      else Def.taskDyn {
        // Only computed for non-empty configurations: `remoteCacheArtifactUrl` needs `remoteCacheId`, which hashes
        // the sources and the external dependency classpath.
        val urlOption = remoteCacheArtifactUrl.value
        val allCredentials = credentials.value
        urlOption match {
          case Some(url) if remoteArtifactExists(url, allCredentials, log) =>
            // Skipping also skips `packageCache`, which unconditionally re-packages the jar on every invocation.
            Def.task(log.info(s"$scopeName: the remote compilation cache artifact already exists, skipping the push"))
          case _ =>
            Def.task(pushRemoteCache.value)
        }
      }
    }.value,
    pullRemoteCacheIfNeeded := Def.taskDyn {
      val log = streams.value.log
      val scopeName = s"${name.value} / ${configuration.value.id}"
      val classDir = classDirectory.value
      // `pullRemoteCache` deletes the class directory before extracting; keep that cleanup for stale outputs of a
      // configuration whose sources have been removed.
      val classDirIsEmpty = !classDir.exists() || IO.listFiles(classDir).isEmpty
      if (hasNoCacheableInputs.value && classDirIsEmpty)
        Def.task(log.debug(s"$scopeName: no sources or resources, skipping the compilation cache pull"))
      else
        Def.task(pullRemoteCache.value)
    }.value
  )

  val compilationCacheSettings: Seq[Setting[?]] =
    inConfig(Compile)(perConfigSettings) ++ inConfig(Test)(perConfigSettings)
}
