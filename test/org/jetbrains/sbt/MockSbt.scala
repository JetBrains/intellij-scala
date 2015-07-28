package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 7/27/15.
 */
trait MockSbt {
  def addSbtAsModuleDependency(module: Module): Unit = {
    val sbtLibrariesRoot = TestUtils.getTestDataPath + "/mockSbt0135/"
    val sbtLibraries = new File(sbtLibrariesRoot).listFiles().filter(f => f.isFile && f.getName.endsWith(".jar"))
    val classesPath = sbtLibraries.map(VfsUtil.getUrlForLibraryRoot)
    ModuleRootModificationUtil.addModuleLibrary(module, "sbt", classesPath.toList.asJava, java.util.Collections.emptyList())
    preventLeakageOfVfsPointers()
  }

  private def preventLeakageOfVfsPointers(): Unit =
    VirtualFilePointerManager.getInstance().asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
}
