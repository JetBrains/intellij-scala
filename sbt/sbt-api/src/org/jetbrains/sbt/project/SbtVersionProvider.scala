package org.jetbrains.sbt.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

import scala.jdk.CollectionConverters.IteratorHasAsScala

@ApiStatus.Internal
@ApiStatus.Experimental
trait SbtVersionProvider {
  def getSbtVersion(module: Module): Option[String]
}

object SbtVersionProvider {
  private val EP = ExtensionPointName.create[SbtVersionProvider]("org.intellij.sbt.sbtVersionProvider")

  def getSbtVersion(module: Module): Option[String] = {
    val implementations = EP.getExtensionList.iterator()
    implementations.asScala.flatMap(_.getSbtVersion(module)).nextOption()
  }
}
