package org.jetbrains.bsp.project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.{JavaModuleTestCase, PlatformTestUtil, VfsTestUtil}
import org.junit.Assert._

class BspUnlinkedProjectAwareTest extends JavaModuleTestCase {

  private val aware = new BspUnlinkedProjectAware

  private def getProjectDir: VirtualFile = {
    val baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(myProject)
    assertNotNull(baseDir)
    baseDir
  }

  def testBspDirWithJson(): Unit = {
    val bspDir = VfsTestUtil.createDir(getProjectDir, ".bsp")
    VfsTestUtil.createFile(bspDir, "sbt.json")
    assertTrue("A .bsp dir with a JSON file should be a considered as BSP build file", aware.isBuildFile(getProject, bspDir))
  }

  def testEmptyBspDir(): Unit = {
    val bspDir = VfsTestUtil.createDir(getProjectDir, ".bsp")
    assertFalse("An empty .bsp dir (no JSON files) shouldn't considered as BSP build file", aware.isBuildFile(getProject, bspDir))
  }

  def testBspDirWithJson_withBuildSbt(): Unit = {
    val dir = getProjectDir
    val bspDir = VfsTestUtil.createDir(dir, ".bsp")
    VfsTestUtil.createFile(bspDir, "sbt.json")
    VfsTestUtil.createFile(dir, "build.sbt")
    assertFalse("A .bsp dir shouldn't be considered as BSP build file when build.sbt exists", aware.isBuildFile(getProject, bspDir))
  }

  def testBloopDir(): Unit = {
    val bloopDir = VfsTestUtil.createDir(getProjectDir, ".bloop")
    assertTrue("A .bloop dir should be considered as BSP build file", aware.isBuildFile(getProject, bloopDir))
  }

  def testMillBuildFile(): Unit = {
    val buildMill = VfsTestUtil.createFile(getProjectDir, "build.mill")
    assertTrue("A build.mill file should be considered as BSP build file", aware.isBuildFile(getProject, buildMill))
  }

  def testMillBuildFile_withBuildSbt(): Unit = {
    val dir = getProjectDir
    val buildMill = VfsTestUtil.createFile(dir, "build.mill")
    VfsTestUtil.createFile(dir, "build.sbt")
    assertFalse("A build.mill file shouldn't be considered as BSP build file when build.sbt exists", aware.isBuildFile(getProject, buildMill))
  }

  def testScalaCliFile(): Unit = {
    val projectScala = VfsTestUtil.createFile(getProjectDir, "project.scala")
    assertTrue("A project.scala file should be considered as BSP build file", aware.isBuildFile(getProject, projectScala))
  }

  def testScalaCliFile_withBuildSbt(): Unit = {
    val dir = getProjectDir
    val projectScala = VfsTestUtil.createFile(dir, "project.scala")
    VfsTestUtil.createFile(dir, "build.sbt")
    assertFalse("A project.scala file shouldn't be considered as BSP build file when build.sbt exists", aware.isBuildFile(getProject, projectScala))
  }
}
