package org.jetbrains.sbt.shell

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.sbt.JvmMemorySize
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, TestInfo}

import java.io.File

class MaxJvmHeapParameterTest {

  val hiddenDefaultSize = JvmMemorySize.Megabytes(1500)
  val hiddenDefaultParam = "-Xmx" + hiddenDefaultSize
  val hardcoded = List("-Dsbt.supershell=false", "-Djdk.console=java.base")

  def buildParamSeq(userOpts: String*)(jvmOpts: String*)(implicit testInfo: TestInfo): Seq[String] = {
    val workingDir = FileUtil.createTempDirectory("maxHeapJvmParamTest", testInfo.getDisplayName, true)

    if (jvmOpts.nonEmpty) {
      val jvmOptsFile = new File(workingDir,".jvmopts")
      FileUtil.writeToFile(jvmOptsFile, jvmOpts.mkString("\n"))
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
      separateProdTestSources = false
    )

    SbtProcessManager.buildVMParameters(settings, workingDir, List.empty)
  }

  /*
    has userOpts xmx =>
       use xmx from userOpts
    has no userOpts xmx =>
       max(hidden default xmx, xms aus jvmopts
   */

  @Test
  def userSettingsSmallerThanHiddenDefault(implicit testInfo: TestInfo): Unit = {
    assertEquals(
      hardcoded ++ Seq("-Xmx4g", "-Xms4g", "-Xmx1g"),
      buildParamSeq("-Xmx1g")("-Xmx4g", "-Xms4g")
    )
  }

  @Test
  def userSettingsGreaterThanHiddenDefault(implicit testInfo: TestInfo): Unit = {
    assertEquals(
      hardcoded ++ Seq("-Xmx4g", "-Xms4g", "-Xmx2g"),
      buildParamSeq("-Xmx2g")("-Xmx4g", "-Xms4g")
    )
  }

  @Test
  def noSettings(implicit testInfo: TestInfo): Unit = {
    assertEquals(
      hiddenDefaultParam +: hardcoded,
      buildParamSeq()()
    )
  }

  @Test
  def noSettingsWithXmsSmallerThanDefaultParam(implicit testInfo: TestInfo): Unit = {
    assertEquals(
      hiddenDefaultParam +: hardcoded :+ "-Xms1g",
      buildParamSeq("-Xms1g")()
    )
  }

  @Test
  def noSettingsWithXmsGreaterThanDefaultParam(implicit testInfo: TestInfo): Unit = {
    assertEquals(
      hardcoded :+ "-Xms2g",
      buildParamSeq("-Xms2g")()
    )
  }
}
