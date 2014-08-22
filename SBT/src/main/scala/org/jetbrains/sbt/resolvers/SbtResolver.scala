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

  override def toString = s"$root$DELIMITER$name"
}

object SbtResolver {
  object Kind extends Enumeration {
    val Maven = Value("maven")
    val Ivy   = Value("ivy")
  }

  val localCacheResolver = {
    val defaultPath = System.getProperty("user.home") + "/.ivy2/cache".replace("/", File.separator)
    SbtResolver(Kind.Ivy, "Local cache", defaultPath)
  }

  val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolver] = {
    str.split(Pattern.quote(DELIMITER), 2).toSeq match {
      case Seq(root, name) => Some(new SbtResolver(name, root))
      case Seq(root) => Some(new SbtResolver("", root))
      case _ => None
    }
  }
}

