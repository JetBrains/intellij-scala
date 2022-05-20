package org.jetbrains.sbt.resolvers

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.plugins.scala.NlsString
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

  // Replicate from MavenIndex function normalizePathOrUrl
  def normalizePathOrUrl(pathOrUrl: String): String =
    FileUtil.toSystemIndependentName(pathOrUrl.trim).reverse.dropWhile(_ == '/').reverse

  def normalizedRoot: String = normalizePathOrUrl(root)
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

  override def toString = s"$root|ivy|isLocal=$isLocal|$name"
}