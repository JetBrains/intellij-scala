package org.jetbrains.plugins.scala.refactoring.move

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path

@RunWith(classOf[JUnit4])
class ScalaMoveClassTest_Scala3 extends ScalaMoveClassTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def getTestDataRoot: Path = super.getTestDataRoot / "scala3"

  @Test
  def testKeepImportsWhenCBHIsEnabled(): Unit = {
    runWithErrorsFromCompiler(getProject) {
      doTest(Seq("com.A"), "org")
    }
  }

  @Test
  def testDontKeepImportsWhenCBHIsDisabled(): Unit = {
    doTest(Seq("com.A"), "org")
  }

  @Test
  def testWithTopLevelDefsInFile_MoveClass(): Unit = {
    doTest(Seq("MyClass"), "")
  }

  @Test
  def testMoveSubtypeOfSealedTrait(): Unit = {
    doTestConflicts(Seq("com.Leaf"), "org",
      "Leaf extends sealed Node and cannot be moved out of Tree.scala, the file that defines Node")
  }

  @Test
  def testMoveSealedTraitWithoutItsSubtypes(): Unit = {
    doTestConflicts(Seq("com.Node"), "org",
      "Sealed Node cannot be moved out of Tree.scala: its subtype Leaf stays there")
  }

  @Test
  def testMoveClassWithQualifiedPrivateOutOfThatPackage(): Unit = {
    doTestConflicts(Seq("com.hex.Tree"), "com.tree",
      "'private[hex]' in Tree refers to package com.hex, which does not enclose com.tree",
      "'private[hex]' in Tree refers to package com.hex, which does not enclose com.tree")
  }

  @Test
  def testMoveClassWithQualifiedPrivateToSubpackageOfThatPackage(): Unit = {
    doTest(Seq("com.hex.Tree"), "com.tree", Kinds.onlyClasses)
  }
}
