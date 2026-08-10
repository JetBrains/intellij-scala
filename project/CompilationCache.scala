import sbt.*
import sbt.KeyRanks.Invisible
import sbt.Keys.*
import sbt.internal.inc.HashUtil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap

object CompilationCache {
  // Defined once in the `Global` scope in `community/build.sbt`, which is loaded both by the standalone community
  // build and by the ultimate build, so all modules share one cache per sbt session.
  lazy val farmHashCache: SettingKey[ConcurrentHashMap[Path, String]] =
    settingKey("Global cache of farm hash values for external dependencies").withRank(Invisible)

  private def farmHash(cache: ConcurrentHashMap[Path, String])(path: Path): String =
    cache.computeIfAbsent(path, HashUtil.farmHash(_).toString)

  private val perConfigSettings: Seq[Setting[?]] = Seq(
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
    }
  )

  val compilationCacheSettings: Seq[Setting[?]] =
    inConfig(Compile)(perConfigSettings) ++ inConfig(Test)(perConfigSettings)
}
