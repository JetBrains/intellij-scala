package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.compiler.CompilerMessageCategory
import org.jetbrains.jps.incremental.scala.utils.ScalaJDKIncompatibilityDetector
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.MavenResolver
import org.jetbrains.plugins.scala.util.runners._
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

/**
 * This test class is designed to validate that the [[org.jetbrains.jps.incremental.scala.utils.ScalaJDKIncompatibilityDetector.JdkCompatibilityWarningPrefix]]
 * is added to the compiler messages when incompatible Scala/JDK versions are detected.
 *
 * @see [[org.jetbrains.jps.incremental.scala.utils.ScalaJDKIncompatibilityDetector]]
 */
@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
abstract class ScalaJdkIncompatibilityTestBase extends ScalaCompilerTestBase {
  @Test
  def failsWithCompatibilityWarning(): Unit = {
    addSources()
    val messages = compiler.make().asScala.toSeq
    val errors = messages.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }
    assertTrue("Expected compilation to fail for an incompatible Scala/JDK versions", errors.nonEmpty)

    val joined = errors.map(_.getMessage).mkString("\n")
    assertTrue(
      s"Expected error message to be prepended with JDK incompatibility warning, but got: \n $joined",
      joined.contains(ScalaJDKIncompatibilityDetector.JdkCompatibilityWarningPrefix)
    )
  }

  private def addSources(): Unit =
    addFileToProjectSources(
      "A.scala",
      """
        |object A {
        |  def main(args: Array[String]): Unit = ()
        |}
        |""".stripMargin
    )
}

/*
 Scala 2.12.0 with JDK 21/25 throws 'case1' from ScalaJDKValidation.containsScalaJdkCompatibilityError
 Scala 2.12.6 with JDK 21/25 throws 'case2' from ScalaJDKValidation.containsScalaJdkCompatibilityError
 */
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12_0, TestScalaVersion.Scala_2_12_6))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_21))
class Scala2_12 extends ScalaJdkIncompatibilityTestBase

// Scala 3.3.0 with JDK 21/25 throws 'case3' from ScalaJDKValidation.containsScalaJdkCompatibilityError
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_3_0))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_21))
class Scala3_3_0 extends ScalaJdkIncompatibilityTestBase

// Scala 3.8 nightly with JDK 11/15/16 caught by ScalaJDKValidation.isScala3_8JdkVersionError
// TODO use Scala 3.8 release when available
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_8_Nightly))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11))
class Scala3Nightly extends ScalaJdkIncompatibilityTestBase  {
  object DependencyManagerWithNightly extends DependencyManagerBase {
    override protected def resolvers: Seq[DependencyManagerBase.Resolver] =
      super.resolvers :+ MavenResolver("scala-maven-nightlies", "https://repo.scala-lang.org/artifactory/maven-nightlies")
  }

  override protected def dependencyManager: DependencyManagerBase = DependencyManagerWithNightly
}
