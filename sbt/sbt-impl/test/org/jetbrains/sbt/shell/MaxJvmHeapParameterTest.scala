package org.jetbrains.sbt.shell

import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.{JvmMemorySize, SbtVersion}
import org.junit.Assert.assertEquals
import org.junit.rules.TestName
import org.junit.{Rule, Test}

import java.nio.file.Files
import scala.util.Using

class MaxJvmHeapParameterTest {

  private val _name: TestName = new TestName()
  @Rule def name: TestName = _name

  private val hiddenDefaultSize = JvmMemorySize.Megabytes(1500)
  private val hiddenDefaultParam = "-Xmx" + hiddenDefaultSize
  private val hardcoded = List("-Dsbt.supershell=false", "-Djdk.console=java.base")

  private def buildParamSeq(userOpts: String*)(jvmOpts: String*): Seq[String] = {
    import org.jetbrains.sbt.PathTestUtil._

    println(name.getMethodName)

    Using.resource(Files.createTempDirectory(s"maxHeapJvmParamTest-${name.getMethodName}")) { workingDir =>
      if (jvmOpts.nonEmpty) {
        val jvmOptsFile = workingDir.resolve(".jvmopts")
        Files.writeString(jvmOptsFile, jvmOpts.mkString("\n"))
      }

      val settings = new SbtExecutionSettings(
        realProjectPath = null,
        vmExecutable = null,
        vmOptions = userOpts,
        sbtOptions = List.empty,
        hiddenDefaultMaxHeapSize = hiddenDefaultSize,
        customLauncher = null,
        customSbtStructureFile = null,
        jdk = null,
        resolveClassifiers = false,
        resolveSbtClassifiers = false,
        useShellForImport = false ,
        shellDebugMode = false,
        preferScala2 = true,
        userSetEnvironment = Map.empty,
        passParentEnvironment = false,
        useSeparateCompilerOutputPaths = false,
        separateProdTestSources = false,
        generateManagedSourcesDuringProjectSync = true,
        sbtVersion = SbtVersion.Latest.Sbt_1
      )

      SbtProcessManager.buildVMParameters(settings, workingDir, List.empty)
    }
  }

  /*
    has userOpts xmx =>
       use xmx from userOpts
    has no userOpts xmx =>
       max(hidden default xmx, xms aus jvmopts
   */

  @Test
  def userSettingsSmallerThanHiddenDefault(): Unit = {
    assertEquals(
      hardcoded ++ Seq("-Xmx4g", "-Xms4g", "-Xmx1g"),
      buildParamSeq("-Xmx1g")("-Xmx4g", "-Xms4g")
    )
  }

  @Test
  def userSettingsGreaterThanHiddenDefault(): Unit = {
    assertEquals(
      hardcoded ++ Seq("-Xmx4g", "-Xms4g", "-Xmx2g"),
      buildParamSeq("-Xmx2g")("-Xmx4g", "-Xms4g")
    )
  }

  @Test
  def noSettings(): Unit = {
    assertEquals(
      hiddenDefaultParam +: hardcoded,
      buildParamSeq()()
    )
  }

  @Test
  def noSettingsWithXmsSmallerThanDefaultParam(): Unit = {
    assertEquals(
      hiddenDefaultParam +: hardcoded :+ "-Xms1g",
      buildParamSeq("-Xms1g")()
    )
  }

  @Test
  def noSettingsWithXmsGreaterThanDefaultParam(): Unit = {
    assertEquals(
      hardcoded :+ "-Xms2g",
      buildParamSeq("-Xms2g")()
    )
  }
}
