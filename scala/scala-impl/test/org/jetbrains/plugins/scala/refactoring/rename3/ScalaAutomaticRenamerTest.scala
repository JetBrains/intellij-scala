package org.jetbrains.plugins.scala.refactoring.rename3

import org.jetbrains.plugins.scala.ScalaVersion

final class ScalaAutomaticRenamerTest extends ScalaRenameTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testAutomaticRenamerOverloads(): Unit = doTest(newName = "bar", withAutoRenames = true)

  def testAutomaticRenamerOverloadsClass(): Unit = doTest(newName = "bar", withAutoRenames = true)

  def testAutomaticRenamerOverloadsExtensionAndMember(): Unit = doTest(newName = "funMe2", withAutoRenames = true)

  def testAutomaticRenamerOverloadsJavaClass(): Unit = doTest(newName = "bar", withAutoRenames = true)
}
