package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManagerForSbt
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.sbt.SbtVersion

/**
 * End-to-end SBT API coverage for [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]] and
 * [[https://youtrack.jetbrains.com/issue/SCL-23772 SCL-23772]].
 */
abstract class SbtSlashSyntaxResolveTestBase extends ScalaLightCodeInsightFixtureTestCase {
  protected def sbtVersion: SbtVersion

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_12

  override protected def additionalCompilerOptions: Seq[String] =
    Seq("-Xsource:3")

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(
      new TestDependencyManagerForSbt(sbtVersion),
      ("org.scala-sbt" % "sbt" % sbtVersion.minor).transitive()
    )
  )

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]
   * Resolves slash syntax from the actual SBT package object.
   */
  def testSCL25850_realSbtSlashSyntax(): Unit = checkTextHasNoErrors(
    """
      |import sbt.Keys.compile
      |
      |object Reproducer {
      |  val scopedPackage = compile / sbt.Keys.packageBin
      |}
      |""".stripMargin
  )

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-25850 SCL-25850]]
   * Keeps the reported explicit-import workaround covered.
   */
  def testSCL25850_realSbtSlashSyntaxWithExplicitImport(): Unit = checkTextHasNoErrors(
    """
      |import sbt.Keys.compile
      |import sbt.sbtSlashSyntaxRichScopeFromScoped
      |
      |object Reproducer {
      |  val scopedPackage = compile / sbt.Keys.packageBin
      |}
      |""".stripMargin
  )

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-23772 SCL-23772]]
   * Resolves `RichTaskSeq.join` through SBT's package prefix.
   */
  def testSCL23772_realSbtTaskKeyJoin(): Unit = checkTextHasNoErrors(
    """
      |import sbt.TaskKey
      |
      |object Reproducer {
      |  val keys: Seq[TaskKey[Unit]] = ???
      |
      |  keys.join
      |}
      |""".stripMargin
  )

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-23772 SCL-23772]]
   * Keeps the explicit `richTaskSeq` import workaround covered.
   */
  def testSCL23772_realSbtTaskKeyJoinWithExplicitImport(): Unit = checkTextHasNoErrors(
    """
      |import sbt.TaskKey
      |import sbt.Scoped.richTaskSeq
      |
      |object Reproducer {
      |  val keys: Seq[TaskKey[Unit]] = ???
      |
      |  keys.join
      |}
      |""".stripMargin
  )

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-23772 SCL-23772]]
   * Resolves the intersection-type `RichTaskSeq` case.
   */
  def testSCL23772_realSbtIntersectionTypeJoin(): Unit = checkTextHasNoErrors(
    """
      |import sbt.{Def, Scoped, Task, TaskKey}
      |
      |object Reproducer {
      |  val keys: Seq[Def.Initialize[Task[Unit]] with Scoped.ScopingSetting[TaskKey[Unit]]] = ???
      |  val initializers: Seq[Def.Initialize[Task[Unit]]] = keys
      |  val settings: Seq[Scoped.ScopingSetting[TaskKey[Unit]]] = keys
      |
      |  keys.join
      |  initializers.join
      |
      |  val richKeys: Scoped.RichTaskSeq[Unit] = keys
      |  val richInitializers: Scoped.RichTaskSeq[Unit] = initializers
      |}
      |""".stripMargin
  )

  /**
   * [[https://youtrack.jetbrains.com/issue/SCL-23772 SCL-23772]]
   * Keeps the explicit import in the intersection-type case covered.
   */
  def testSCL23772_realSbtIntersectionTypeJoinWithExplicitImport(): Unit = checkTextHasNoErrors(
    """
      |import sbt.{Def, Scoped, Task, TaskKey}
      |import sbt.Scoped.richTaskSeq
      |
      |object Reproducer {
      |  val keys: Seq[Def.Initialize[Task[Unit]] with Scoped.ScopingSetting[TaskKey[Unit]]] = ???
      |  val initializers: Seq[Def.Initialize[Task[Unit]]] = keys
      |  val settings: Seq[Scoped.ScopingSetting[TaskKey[Unit]]] = keys
      |
      |  keys.join
      |  initializers.join
      |
      |  val richKeys: Scoped.RichTaskSeq[Unit] = keys
      |  val richInitializers: Scoped.RichTaskSeq[Unit] = initializers
      |}
      |""".stripMargin
  )
}

/** Uses the first SBT line supported by the logger's SBT 1 artifact. */
class SbtSlashSyntaxResolveTest_Sbt_1_4 extends SbtSlashSyntaxResolveTestBase {
  override protected val sbtVersion: SbtVersion = SbtVersion("1.4.9")
}

/** Matches the real-SBT setup used in SCL-23772's Scala 2.12 reproduction. */
class SbtSlashSyntaxResolveTest_Sbt_1_11 extends SbtSlashSyntaxResolveTestBase {
  override protected val sbtVersion: SbtVersion = SbtVersion.Latest.Sbt_1_11
}
