package org.jetbrains.bsp.project.resolver

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors.ModuleDescription

trait BspResolverNamingExtension {
  def libraryData(moduleDescription: ModuleDescription): Option[String]

  def libraryTestData(moduleDescription: ModuleDescription): Option[String]
}

object BspResolverNamingExtension {
  val EP_NAME =
    ExtensionPointName.create[BspResolverNamingExtension]("com.intellij.bspResolverNamingExtension")

  def libraryData(moduleDescription: ModuleDescription): Option[String] = {
    get(_.libraryData(moduleDescription))
  }

  def libraryTestData(moduleDescription: ModuleDescription): Option[String] = {
    get(_.libraryTestData(moduleDescription))
  }

  private def get(apply: BspResolverNamingExtension => Option[String]): Option[String] = {
    EP_NAME.getExtensions.iterator.map(apply).collectFirst { case Some(r) => r }
  }

}
