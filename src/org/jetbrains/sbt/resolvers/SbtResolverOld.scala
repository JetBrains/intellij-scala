package org.jetbrains.sbt
package resolvers

import java.io.File
import java.util.regex.Pattern

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */
case class SbtResolverOld(kind: SbtResolverOld.Kind.Value, name: String, root: String) {
  import org.jetbrains.sbt.resolvers.SbtResolverOld._
//  def associatedIndex: Option[SbtResolverIndex] = SbtResolverIndexesManager().find(this)

  override def toString = s"$root$DELIMITER$kind$DELIMITER$name"
}

object SbtResolverOld {
  object Kind extends Enumeration {
    val Maven = Value(0, "maven")
    val Ivy   = Value(1, "ivy")
  }

  def localCacheResolver(localCachePath: Option[String]): SbtResolverOld = {
    val defaultPath = System.getProperty("user.home") + "/.ivy2/cache".replace('/', File.separatorChar)
    SbtResolverOld(Kind.Ivy, "Local cache", localCachePath getOrElse defaultPath)
  }

  val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolverOld] = {
    str.split(Pattern.quote(DELIMITER), 3).toSeq match {
      case Seq(root, kind, name) => Some(new SbtResolverOld(Kind.withName(kind), name, root))
      case Seq(root, kind) => Some(new SbtResolverOld(Kind.withName(kind), "", root))
      case _ => None
    }
  }
}

