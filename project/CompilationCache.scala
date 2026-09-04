import sbt.*
import sbt.KeyRanks.Invisible
import sbt.Keys.*
import sbt.internal.inc.HashUtil

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.Base64
import java.util.concurrent.{ConcurrentHashMap, Executors, Semaphore, TimeUnit}
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.util.Try
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

  // The path of the remote cache artifact for the current configuration and remoteCacheId, relative to a repository
  // root, mirroring the coordinates used by sbt's own `pushRemoteCache`/`pullRemoteCache` (see
  // `RemoteCache.pullRemoteCache`): maven-style layout, revision `0.0.0-<remoteCacheId>`, classifier
  // `cached-compile`/`cached-test`. The cross-version suffix must be derived via `CrossVersion` because some modules
  // set `crossPaths := false`.
  // Relative, so that the same coordinates address both the artifact in the remote repository and its copy in the
  // local mirror (see `compilationCacheArtifact`).
  private def remoteCacheArtifactPath: Def.Initialize[Task[String]] = Def.task {
    val projectId = remoteCacheProjectId.value
    val crossName = CrossVersion(projectId, scalaModuleInfo.value).getOrElse(identity[String] _)
    val cacheArtifact = (packageCache / artifact).value
    val name = crossName(cacheArtifact.name)
    val classifierSuffix = cacheArtifact.classifier.fold("")(c => s"-$c")
    val organizationPath = projectId.organization.replace('.', '/')
    s"$organizationPath/$name/${projectId.revision}/" +
      s"$name-${projectId.revision}$classifierSuffix.${cacheArtifact.extension}"
  }

  // `None` when the remote cache repository is not a maven-style one, in which case these coordinates do not apply.
  private def cacheArtifactCoordinates: Def.Initialize[Task[Option[CacheArtifact]]] = Def.task {
    val path = remoteCacheArtifactPath.value
    pushRemoteCacheTo.value.collect { case repository: MavenRepository =>
      CacheArtifact(s"${repository.root.stripSuffix("/")}/$path", path)
    }
  }

  private val requestTimeout = 30.seconds

  // The client is immutable and thread-safe; sharing one instance lets the HEAD requests issued for all modules
  // by `pushCompilationCacheAll` reuse pooled connections to the repository host.
  private lazy val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(java.time.Duration.ofMillis(requestTimeout.toMillis))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  private def requestBuilderFor(uri: URI, allCredentials: Seq[Credentials]): HttpRequest.Builder = {
    val requestBuilder = HttpRequest.newBuilder(uri)
      .timeout(java.time.Duration.ofMillis(requestTimeout.toMillis))
    Credentials.forHost(allCredentials, uri.getHost).foreach { credentials =>
      val token = Base64.getEncoder.encodeToString(
        s"${credentials.userName}:${credentials.passwd}".getBytes(StandardCharsets.UTF_8)
      )
      requestBuilder.header("Authorization", s"Basic $token")
    }
    requestBuilder
  }

  private def remoteArtifactExists(url: String, allCredentials: Seq[Credentials], log: Logger): Boolean =
    try {
      val uri = new URI(url)
      val requestBuilder = requestBuilderFor(uri, allCredentials)
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
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

  final case class CacheArtifact(url: String, path: String)

  lazy val compilationCacheArtifact: TaskKey[Option[CacheArtifact]] = taskKey[Option[CacheArtifact]](
    "Coordinates of this configuration's compilation cache artifact, or None when the configuration has nothing to cache"
  ).withRank(Invisible)

  // One mirror for the whole build, alongside the other derived output: `target/` is gitignored and already owned by
  // `cleanAll`, which the CI command line runs before the pull.
  def mirrorDirectory: Def.Initialize[File] =
    Def.setting((ThisBuild / baseDirectory).value / "target" / "compilation-cache-mirror")

  // Each artifact costs ~250ms against the repository host, almost all of it connection setup rather than transfer,
  // and sbt's own pull cannot overlap them: `RemoteCache.pullFromMavenRepo0` goes through Apache Ivy, and every Ivy
  // operation in sbt runs inside a global lock (`IvySbt.withDefaultLogger`). Fetching the artifacts concurrently
  // ourselves and letting the pull resolve them from a local mirror keeps sbt in charge of the delicate part, the
  // extraction and the Zinc analysis remapping.
  // Requests in flight at once, bounded rather than maximal and not configurable: `pullCompilationCacheAll` is part
  // of the shared sbt step, so every test job runs this too, and they all start off the same package build. The
  // number is therefore multiplied by however many jobs happen to be running at once, instead of applying to one
  // build in isolation — which is also why it cannot be derived from anything the local process can observe.
  private val prefetchConcurrency = 16
  private val prefetchAttempts = 3
  private val prefetchTimeout = 3.minutes
  // Cap on what a `Retry-After` is allowed to make us wait: the pre-fetch is on the critical path of every build, and
  // waiting minutes for one artifact is worse than treating it as a failure and letting the pull fetch it directly.
  private val maxRetryDelay = 30.seconds

  private sealed trait FetchResult
  private object FetchResult {
    // Written to the mirror, so the pull resolves it locally.
    case object Fetched extends FetchResult
    // Not in the repository (HTTP 404): a genuine cache miss, which the pull reports and the module then recompiles.
    case object Absent extends FetchResult
    // Anything else. The pull falls back to the remote repository for these, so they cost time rather than a rebuild.
    case object Failed extends FetchResult
  }

  /**
   * Downloads `artifacts` into `mirror` concurrently, in maven-style layout, so that the mirror resolver placed in
   * front of the remote repository (see `remoteCacheResolvers` below) finds them without touching the network.
   *
   * Never fails the build: an artifact that cannot be fetched is simply absent from the mirror, and the pull falls
   * back to the remote repository for it — exactly the behaviour from before the mirror existed.
   */
  def prefetchArtifacts(
    mirror: File,
    artifacts: Seq[CacheArtifact],
    allCredentials: Seq[Credentials],
    log: Logger
  ): Unit = {
    if (artifacts.isEmpty) {
      log.debug("Compilation cache pre-fetch: no artifacts to fetch")
    } else {
      val startedAt = System.nanoTime()
      // One virtual thread per artifact: they spend their whole life blocked on a socket, so they unmount instead of
      // occupying a carrier thread, and there is no pool to size. The bound therefore has to be explicit — a virtual
      // thread executor throttles nothing, and without the semaphore this would open one connection per artifact.
      val threadFactory = Thread.ofVirtual().name("compilation-cache-prefetch-", 0).factory()
      val pool = Executors.newThreadPerTaskExecutor(threadFactory)
      implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(pool)
      val inFlight = new Semaphore(prefetchConcurrency)
      val fetches = artifacts.map { artifact =>
        Future {
          inFlight.acquire()
          try fetchArtifact(mirror, artifact, allCredentials, log)
          finally inFlight.release()
        }
      }
      val results = try {
        Await.result(Future.sequence(fetches), prefetchTimeout)
      } catch {
        case _: TimeoutException =>
          log.warn(s"Compilation cache pre-fetch did not finish within $prefetchTimeout, continuing without the rest")
          pool.shutdownNow()
          // Whatever finished is already in the mirror; the pull resolves the rest over the network. An interrupted
          // fetch leaves at most a `.part` file, which nothing ever reads.
          fetches.flatMap(_.value).map(_.getOrElse(FetchResult.Failed))
      } finally {
        pool.shutdown()
      }
      val elapsed = (System.nanoTime() - startedAt).nanos
      // `toUnit` gives the fractional value in the requested unit, so seconds stay readable at sub-second durations
      // without dividing by a hand-written factor.
      val elapsedSeconds = f"${elapsed.toUnit(TimeUnit.SECONDS)}%.1f"
      log.info(
        s"Compilation cache pre-fetch: ${results.count(_ == FetchResult.Fetched)} artifact(s) into the local mirror, " +
          s"${results.count(_ == FetchResult.Absent)} not in the repository, " +
          s"${results.count(_ == FetchResult.Failed)} failed, in $elapsedSeconds s"
      )
    }
  }

  // `Retry-After` is defined as either a number of seconds or an HTTP date; only the former is honoured, and an
  // unparseable value simply falls back to the caller's own backoff.
  private def retryAfterDelay(response: HttpResponse[?]): Option[FiniteDuration] = {
    val header = response.headers().firstValue("Retry-After")
    if (header.isPresent) Try(header.get().toInt.seconds).toOption else None
  }

  private def fetchArtifact(
    mirror: File,
    artifact: CacheArtifact,
    allCredentials: Seq[Credentials],
    log: Logger
  ): FetchResult = {
    val target = mirror.toPath.resolve(artifact.path)
    val partial = target.resolveSibling(s"${target.getFileName}.part")

    def attemptFetch(attempt: Int): FetchResult = {
      val request = requestBuilderFor(new URI(artifact.url), allCredentials).GET().build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(partial))
      response.statusCode() match {
        case 200 =>
          // Moved into place only once the whole body has arrived, so that an interrupted build cannot leave a
          // truncated jar behind for the mirror to serve as a cache hit.
          Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
          FetchResult.Fetched
        case 404 =>
          // A genuine cache miss: the module's inputs changed, so no artifact was ever pushed for this id.
          Files.deleteIfExists(partial)
          log.debug(s"No compilation cache artifact at ${artifact.url}")
          FetchResult.Absent
        case 429 if attempt < prefetchAttempts =>
          Files.deleteIfExists(partial)
          val delay = retryAfterDelay(response).getOrElse(attempt.seconds).min(maxRetryDelay)
          Thread.sleep(delay.toMillis)
          attemptFetch(attempt + 1)
        case code =>
          Files.deleteIfExists(partial)
          log.warn(s"Unexpected HTTP status $code while pre-fetching the compilation cache artifact ${artifact.url}")
          FetchResult.Failed
      }
    }

    try {
      Files.createDirectories(target.getParent)
      attemptFetch(attempt = 1)
    } catch {
      case NonFatal(e) =>
        Files.deleteIfExists(partial)
        log.warn(s"Could not pre-fetch the compilation cache artifact ${artifact.url}: ${e.getMessage}")
        FetchResult.Failed
    }
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
        // Only computed for non-empty configurations: `cacheArtifactCoordinates` needs `remoteCacheId`, which hashes
        // the sources and the external dependency classpath.
        val urlOption = cacheArtifactCoordinates.value.map(_.url)
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
    }.value,
    compilationCacheArtifact := Def.taskDyn {
      if (hasNoCacheableInputs.value)
        Def.task(Option.empty[CacheArtifact])
      else
        cacheArtifactCoordinates
    }.value
  )

  // `pullRemoteCache` resolves from `remoteCacheResolvers`, while `pushRemoteCache` publishes to `pushRemoteCacheTo`
  // (see `RemoteCache.configCacheSettings`), so the local mirror can be put in front of the remote repository for
  // pulls without affecting pushes at all.
  // The remote repository stays behind the mirror on purpose: anything the pre-fetch did not place resolves over the
  // network exactly as it did before, so a pre-fetch that fails or misses an artifact costs at worst the time it used
  // to cost, instead of turning into a cache miss and a module recompile.
  //
  // Two details, both established by experiment and both easy to get wrong:
  //
  // 1. The mirror and the remote repository must be wrapped in a single `ChainedResolver`, not listed as two entries.
  //    sbt's pull consults only the *head* of `remoteCacheResolvers`: with `Seq(mirror, remote)` an artifact missing
  //    from the mirror is reported as a cache miss even though the remote repository has it. Inside a chain both are
  //    tried, and a hit in the mirror short-circuits, so a mirror hit still costs no network round-trip.
  // 2. These settings are project-scoped, not per configuration. sbt's `RemoteCache.projectSettings` builds the Ivy
  //    instance the pull runs on (`pushRemoteCache / ivyConfiguration`) from the *project*-scoped
  //    `remoteCacheResolvers` and `pushRemoteCacheTo`; a resolver named only in `Compile`/`Test` is never registered
  //    there and the pull fails with `undefined resolver 'compilation-cache-mirror'`.
  private val perProjectSettings: Seq[Setting[?]] = Seq(
    remoteCacheResolvers := {
      val mirror = MavenRepository("compilation-cache-mirror", mirrorDirectory.value.toURI.toString)
      Seq(ChainedResolver("compilation-cache", mirror +: pushRemoteCacheTo.value.toVector))
    }
  )

  val compilationCacheSettings: Seq[Setting[?]] =
    perProjectSettings ++ inConfig(Compile)(perConfigSettings) ++ inConfig(Test)(perConfigSettings)
}
