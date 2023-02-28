package org.jetbrains.sbt.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

import java.net.URI
import scala.jdk.CollectionConverters.IteratorHasAsScala

@ApiStatus.Internal
@ApiStatus.Experimental
trait SbtBuildModuleUriProvider {
  /**
   * For a given `module` this method returns URI of it's corresponding build module.<br>
   * For example in this project structure {{{
   *   <root> //~ corresponds to IntelliJ module `root`
   *     |-- project //~ corresponds to IntelliJ module `root-build`
   *     |-- subProject1 //~ corresponds to IntelliJ module `subProject1`
   *     |-- subProject2 //~ corresponds to IntelliJ module `subProject2`
   *     |-- build.sbt
   * }}}
   * for modules `root`, `subProject1` and `subProject2` it will return URI or `root`<br>
   * for module `root-build` (corresponding to a build module itself) it will return `None`
   */
  def getBuildModuleUri(module: Module): Option[URI]
}

object SbtBuildModuleUriProvider {

  private val EP = ExtensionPointName.create[SbtBuildModuleUriProvider]("org.intellij.sbt.buildModuleUriProvider")

  def getBuildModuleUri(module: Module): Option[URI] = {
    val implementations = EP.getExtensionList.iterator()
    implementations.asScala.flatMap(_.getBuildModuleUri(module)).nextOption()
  }
}
