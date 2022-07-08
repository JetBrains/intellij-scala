package org.jetbrains.sbt.resolvers

import com.intellij.openapi.util.io.FileUtil
import com.intellij.serialization.PropertyMapping
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString

sealed trait SbtResolver extends Serializable {
  def name: String
  def root: String
  @Nls
  def presentableName: String
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

  override def toString = s"$root|ivy|isLocal=$isLocal|$name"
}