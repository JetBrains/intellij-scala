package org.jetbrains.plugins.scala.collectors

import com.intellij.facet.frameworks.LibrariesDownloadConnectionService
import com.intellij.internal.statistic.libraryJar.LibraryJarDescriptors
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.xmlb.XmlSerializer

import java.net.URL
import scala.util.Try

object CompilerPluginsWhiteList {

  lazy val get: Set[String] = {
    val result = for {
      url <- createRequestUrl.toSeq
      descriptors <- deserialize(url).toSeq
      descriptor <- descriptors.getDescriptors.toSeq
    } yield descriptor.myName
    result.toSet
  }

  private def createRequestUrl: Option[URL] = {
    val serviceUrl = LibrariesDownloadConnectionService.getInstance.getServiceUrl
    val url = s"$serviceUrl/statistics/scalac-plugins-statistics.xml"
    Try {
      HttpConfigurable.getInstance.prepareURL(url)
      new URL(url)
    }.toOption
  }

  private def deserialize(url: URL): Option[LibraryJarDescriptors] =
    Try(XmlSerializer.deserialize(url, classOf[LibraryJarDescriptors])).toOption
}
