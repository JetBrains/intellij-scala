package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
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
  def addSbtAsModuleDependency(module: Module): Unit = {
    val sbtLibraries = Seq("collections", "interface", "io", "ivy", "logging", "main", "main-settings", "process", "sbt")
      .map(n => new File(TestUtils.getIvyCachePath + s"/org.scala-sbt/$n/jars/$n-0.13.5.jar"))
    val scala210 = ScalaSdkVersion._2_10
    val scalaLibraryJars = Seq(
      TestUtils.getScalaLibraryPath(scala210),
      TestUtils.getScalaCompilerPath(scala210),
      TestUtils.getScalaReflectPath(scala210)
    ).map(new File(_))
    val classesPath = (sbtLibraries ++ scalaLibraryJars).map(VfsUtil.getUrlForLibraryRoot)
    ModuleRootModificationUtil.addModuleLibrary(module, "sbt", classesPath.toList.asJava, java.util.Collections.emptyList())
    preventLeakageOfVfsPointers()
  }

  def preventLeakageOfVfsPointers(): Unit =
    VirtualFilePointerManager.getInstance().asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
}
