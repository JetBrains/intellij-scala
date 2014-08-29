package org.jetbrains.sbt
package resolvers

import java.util.regex.Pattern
import java.io.File

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */
case class SbtResolver(kind: SbtResolver.Kind.Value, name: String, root: String) {
  import SbtResolver._
  def associatedIndex = SbtResolverIndexesManager().find(this)

  override def toString = s"$root$DELIMITER$kind$DELIMITER$name"
}

object SbtResolver {
  object Kind extends Enumeration {
    val Maven = Value(0, "maven")
    val Ivy   = Value(1, "ivy")
  }

  def localCacheResolver(localCachePath: Option[String]) = {
    val defaultPath = System.getProperty("user.home") + "/.ivy2/cache".replace('/', File.separatorChar)
    SbtResolver(Kind.Ivy, "Local cache", localCachePath getOrElse defaultPath)
  }

  val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolver] = {
    str.split(Pattern.quote(DELIMITER), 3).toSeq match {
      case Seq(root, kind, name) => Some(new SbtResolver(Kind.withName(kind), name, root))
      case Seq(root, kind) => Some(new SbtResolver(Kind.withName(kind), "", root))
      case _ => None
    }
  }
}

