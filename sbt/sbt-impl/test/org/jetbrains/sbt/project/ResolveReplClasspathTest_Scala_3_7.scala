package org.jetbrains.sbt.project

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt, ReplClasspath}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Category(Array(classOf[SlowTests2]))
@RunWith(classOf[JUnit4])
class ResolveReplClasspathTest_Scala_3_7 extends SbtExternalSystemImportingTestLike:

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/resolveReplClasspath_Scala37"

  @Test
  def resolveReplClasspath(): Unit =
    importProject(false)

    val scalaSdk = getMyProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk.asInstanceOf[LibraryEx].properties

    val replClasspath = properties.replClasspath
    assertEquals("The REPL classpath was not configured correctly", ReplClasspath.Bundled, replClasspath)
