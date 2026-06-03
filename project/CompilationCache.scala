import sbt.*
import sbt.KeyRanks.Invisible
import sbt.Keys.*
import sbt.internal.inc.HashUtil

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap

object CompilationCache {
  lazy val farmHashCache: SettingKey[ConcurrentHashMap[Path, String]] =
    settingKey("Global cache of farm hash values for external dependencies").withRank(Invisible)

  private def farmHash(cache: ConcurrentHashMap[Path, String])(path: Path): String =
    cache.computeIfAbsent(path, HashUtil.farmHash(_).toString)

  private val perConfigSettings: Seq[Setting[?]] = Seq(
    pushRemoteCacheConfiguration ~= { _.withOverwrite(true) },
    remoteCacheId := {
      val id = remoteCacheId.value
      val classpath = externalDependencyClasspath.value.map(_.data.toPath).filter(Files.exists(_)).sorted
      val cache = (Global / farmHashCache).?.value.getOrElse(new ConcurrentHashMap())
      val hashString = classpath.map(farmHash(cache)).mkString
      val combined = id ++ hashString
      val hash = HashUtil.farmHash(combined.getBytes(StandardCharsets.UTF_8))
      java.lang.Long.toHexString(hash)
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
