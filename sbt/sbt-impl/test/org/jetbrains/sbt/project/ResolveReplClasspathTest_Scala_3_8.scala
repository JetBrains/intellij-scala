package org.jetbrains.sbt.project

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt, ReplClasspath}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Category(Array(classOf[SlowTests2]))
@RunWith(classOf[JUnit4])
class ResolveReplClasspathTest_Scala_3_8 extends SbtExternalSystemImportingTestLike:

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/resolveReplClasspath_Scala38"

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  @Test
  def resolveReplClasspath(): Unit =
    importProject(false)

    val scalaSdk = getMyProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath

    assertTrue("The REPL classpath was not configured correctly", replClasspath.isInstanceOf[ReplClasspath.Provided])
    val paths = replClasspath.asPaths
    assertTrue("The REPL classpath is empty", paths.nonEmpty)
    val scala3ReplJar = paths.find(_.getFileName.toString == "scala3-repl_3-3.8.0-RC1.jar")
    assertTrue("Could not find the scala3-repl_3 jar on the REPL classpath", scala3ReplJar.isDefined)
