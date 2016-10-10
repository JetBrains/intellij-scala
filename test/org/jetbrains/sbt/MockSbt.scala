package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 7/27/15.
 */
trait MockSbt {

  def sbtVersion: String

  def addSbtAsModuleDependency(module: Module): Unit = {

    val sbtLibVersion = Option(sbtVersion).getOrElse(Sbt.LatestVersion)

    val sbtLibraries = Seq("collections", "interface", "io", "ivy", "logging", "main", "main-settings", "process", "sbt")
      .map(n => new File(TestUtils.getIvyCachePath + s"/org.scala-sbt/$n/jars/$n-$sbtVersion.jar"))
    val scalaLibrary = ScalaSdkVersion._2_10
    val scalaLibraryJars = Seq(
      TestUtils.getScalaLibraryPath(scalaLibrary),
      TestUtils.getScalaCompilerPath(scalaLibrary),
      TestUtils.getScalaReflectPath(scalaLibrary)
    ).map(new File(_))
    val classesPath = (sbtLibraries ++ scalaLibraryJars).map(VfsUtil.getUrlForLibraryRoot)
    ModuleRootModificationUtil.addModuleLibrary(module, "sbt", classesPath.toList.asJava, java.util.Collections.emptyList())
    preventLeakageOfVfsPointers()
  }

  def preventLeakageOfVfsPointers(): Unit =
    VirtualFilePointerManager.getInstance().asInstanceOf[VirtualFilePointerManagerImpl].storePointers()

}
