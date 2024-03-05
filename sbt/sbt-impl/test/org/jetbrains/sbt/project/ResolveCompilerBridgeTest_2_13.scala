package org.jetbrains.sbt.project

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.project.{LibraryExExt, LibraryExt, ProjectExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, assertNotNull}
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class ResolveCompilerBridgeTest_2_13 extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/resolveCompilerBridge_Scala2"

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
  }

  def testResolveCompilerBridge(): Unit = {
    importProject(false)

    // defined in the test project `resolveCompilerBridge_Scala2/build.sbt`
    val scalaVersion = "2.13.13"

    val scalaSdk = myProject.libraries.find(_.isScalaSdk).orNull
    assertNotNull("Scala SDK not configured", scalaSdk)

    val properties = scalaSdk match {
      case ex: LibraryEx => ex.properties
    }

    val compilerBridge = properties.compilerBridgeBinaryJar.orNull
    assertNotNull(s"Scala 2 compiler bridge not configured", compilerBridge)

    assertEquals(s"scala2-sbt-bridge-$scalaVersion.jar", compilerBridge.getName)
  }
}
