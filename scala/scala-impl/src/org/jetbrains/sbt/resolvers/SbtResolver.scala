package org.jetbrains.sbt.resolvers

import java.io.File
import java.util.regex.Pattern
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.project.Project
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.indices.{MavenIndex, MavenIndicesManager}
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.resolvers.indexes.{FakeMavenIndex, MavenProxyIndex, ResolverIndex}

/**
  * @author Mikhail Mutcianko
  * @since 26.07.16
  */
sealed trait SbtResolver extends Serializable {
  def name: String
  def root: String
  @Nls
  def presentableName: String
  def getIndex(project: Project): Option[ResolverIndex]
  override def hashCode(): Int = toString.hashCode
  override def equals(o: scala.Any): Boolean = toString == o.toString
}

object SbtResolver {
  def localCacheResolver(localCachePath: Option[String]): SbtResolver = {
    val defaultPath = System.getProperty("user.home") + "/.ivy2/cache".replace('/', File.separatorChar)
    new SbtIvyResolver("Local cache", localCachePath getOrElse defaultPath, isLocal = true, SbtBundle.message("sbt.local.cache"))
  }

  private val DELIMITER = "|"
  def fromString(str: String): Option[SbtResolver] = {
    str.split(Pattern.quote(DELIMITER), 3).toSeq match {
      case Seq(root, "maven", name) => Some(new SbtMavenResolver(name, root))
      case Seq(root, "ivy", name) => Some(new SbtIvyResolver(name, root, isLocal = false))
      case _ => None
    }
  }
}

final class SbtMavenResolver @PropertyMapping(Array("name", "root", "presentableName"))
(
  override val name: String,
  override val root: String,
  @Nls _presentableName: String = null
) extends SbtResolver {

  override val presentableName: String =
    if (_presentableName != null) _presentableName else NlsString.force(name)

  override def getIndex(project: Project): Option[ResolverIndex] = try {
      MavenIndicesManager.getInstance(project)
      Some(new MavenProxyIndex(root, name, project))
    } catch {
    case _: PluginException =>
      Some(new FakeMavenIndex(root, name, project))
    case e: NoClassDefFoundError if e.getMessage.contains("MavenIndicesManager") =>
      Some(new FakeMavenIndex(root, name, project))
  }

  override def toString = s"$root|maven|$name"

  def normalizedRoot: String = MavenIndex.normalizePathOrUrl(root)
}

final class SbtIvyResolver @PropertyMapping(Array("name", "root", "isLocal", "presentableName"))
(
  override val name: String,
  override val root: String,
  val isLocal: Boolean,
  @Nls _presentableName: String = null
) extends SbtResolver {

  override val presentableName: String =
    if (_presentableName != null) _presentableName else NlsString.force(name)

  override def getIndex(project: Project): Option[ResolverIndex] =
    SbtIndexesManager.getInstance(project).map(_.getIvyIndex(name, root))

  override def toString = s"$root|ivy|$name"
}