package org.jetbrains.plugins.scala.refactoring.move.directory

import com.intellij.idea.TestFor
import com.intellij.openapi.vfs.{VirtualFileEvent, VirtualFileListener, VirtualFileManager}
import junit.framework.TestCase.assertFalse
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsJUnit4Runner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.nowarn

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
class ScalaMoveDirectoryWithClassesTest extends ScalaMoveDirectoryWithClassesTestBase {
  @Test
  def testRenamePackage(): Unit = doRenamePackageTest("pack1", "pack1.pack2")

  @Test
  @TestFor(issues = Array("SCL-24393"))
  def testRenamePackageInJavaProject(): Unit =
    doRenamePackageTest("com.something.sub.mypackage", "com.something.mypackage")

  @Test
  def testRenamePackageUp(): Unit = doRenamePackageTest("pack1.pack2", "pack1")

  @Test
  def testRenamePackageImportsToNestedClasses(): Unit = doRenamePackageTest("pack1.pack2", "pack0.pack2")

  @Test
  def testRenamePackageWithPackageObject(): Unit = doRenamePackageTest("pack1.pack2", "pack0.pack1.pack2")

  @Test
  def testMovePackageWithPackageObject(): Unit = doMovePackageTest("pack1.pack2", "pack3")

  @Test
  def testMultipleClassesInOneFile(): Unit = {
    var fileWasDeleted = false
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener {
      override def fileDeleted(event: VirtualFileEvent): Unit = if (!event.getFile.isDirectory) fileWasDeleted = true
    }, getTestRootDisposable): @nowarn("cat=deprecation")

    doMovePackageTest()
    assertFalse("Deleted instead of move", fileWasDeleted)
  }

  @Test
  def testEmptySubDirs(): Unit = doMovePackageTest()
}
