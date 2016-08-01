package org.jetbrains.sbt.resolvers

import java.io.File
import java.util.regex.Pattern

import org.jetbrains.sbt.resolvers.indexes.{MavenProxyIndex, ResolverIndex}

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
trait SbtResolver {
  def name: String
  def root: String
  def getIndex: ResolverIndex
}

object SbtResolver {
  def localCacheResolver(localCachePath: Option[String]): SbtResolver = {
    val defaultPath = System.getProperty("user.home") + "/.ivy2/cache".replace('/', File.separatorChar)
    new SbtIvyResolver("Local cache", localCachePath getOrElse defaultPath)
  }

  val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolver] = {
    str.split(Pattern.quote(DELIMITER), 3).toSeq match {
      case Seq(root, "maven", name) => Some(new SbtMavenResolver(name, root))
      case Seq(root, "ivy", name) => Some(new SbtIvyResolver(name, root))
      case _ => None
    }
  }
}

class SbtMavenResolver(val name: String, val root: String) extends SbtResolver {
  override lazy val getIndex: ResolverIndex = new MavenProxyIndex(root, name)
  override def toString = s"$root|maven|$name"
}

class SbtIvyResolver(val name: String, val root: String) extends SbtResolver {
  override lazy val getIndex: ResolverIndex = ResolverIndex.createOrLoadIvy(name, root)
  override def toString = s"$root|ivy|$name"
}