package org.jetbrains.sbt.resolvers

import java.io.File
import java.util.regex.Pattern

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.sbt.resolvers.indexes.{FakeMavenIndex, MavenProxyIndex, ResolverIndex}

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
trait SbtResolver extends Serializable {
  def name: String
  def root: String
  def getIndex(project: Project): ResolverIndex
  override def hashCode(): Int = toString.hashCode
  override def equals(o: scala.Any): Boolean = toString == o.toString
}

object SbtResolver {
  def localCacheResolver(localCachePath: Option[String]): SbtResolver = {
    val defaultPath = System.getProperty("user.home") + "/.ivy2/cache".replace('/', File.separatorChar)
    new SbtIvyResolver("Local cache", localCachePath getOrElse defaultPath)
  }

  private val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolver] = {
    str.split(Pattern.quote(DELIMITER), 3).toSeq match {
      case Seq(root, "maven", name) => Some(new SbtMavenResolver(name, root))
      case Seq(root, "ivy", name) => Some(new SbtIvyResolver(name, root))
      case _ => None
    }
  }
}

class SbtMavenResolver(val name: String, val root: String) extends SbtResolver {
  override def getIndex(project: Project): ResolverIndex = try {
      MavenIndicesManager.getInstance()
      new MavenProxyIndex(root, name)
    } catch {
      case e:NoClassDefFoundError if e.getMessage.contains("MavenIndicesManager") =>
        new FakeMavenIndex(root, name)
    }

  override def toString = s"$root|maven|$name"
}

class SbtIvyResolver(val name: String, val root: String) extends SbtResolver {
  override def getIndex(project: Project): ResolverIndex = SbtIndexesManager.getInstance(project).getIvyIndex(name, root)
  override def toString = s"$root|ivy|$name"
}