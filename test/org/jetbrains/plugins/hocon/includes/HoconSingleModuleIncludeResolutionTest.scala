package org.jetbrains.plugins.hocon.includes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.hocon.HoconLightMultiFileTestCase

class HoconSingleModuleIncludeResolutionTest extends HoconLightMultiFileTestCase with HoconIncludeResolutionTest {

  protected def project: Project = LightPlatformTestCase.getProject

  protected def contentRoots: Array[VirtualFile] =
    Array(LocalFileSystem.getInstance.findFileByPath(rootPath))

  protected def rootPath = baseRootPath + "includes/singlemodule"

  def testIncludesFromToplevel(): Unit = {
    checkFile("including.conf")
  }

  def testIncludesFromWithinPackage(): Unit = {
    checkFile("pkg/including.conf")
  }
}
