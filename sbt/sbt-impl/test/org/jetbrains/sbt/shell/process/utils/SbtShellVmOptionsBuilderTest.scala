package org.jetbrains.sbt.shell.process.utils

import com.intellij.testFramework.{TestApplicationManager, UsefulTestCase}
import com.intellij.util.EnvironmentUtil
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.shell.SbtProcessManager.SbtShellVmOptionsData
import org.jetbrains.sbt.{JvmMemorySize, SbtVersion}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.{Rule, Test}

import java.nio.file.{Files, Path}
import java.util.function.Supplier
import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsJava, MapHasAsScala}
import scala.util.Using

/**
 * NOTE:<br>
 * Lots of test expectations in the current test class exist primarily to detect potential regressions.<br>
 * The current expected result is mostly the baked behavior when this test class was written.<br>
 * While improving production logic for the sbt shell process, we will need to revisit these expectations.
 *
 * This class extends [[UsefulTestCase]] because it covers [[SbtShellVmOptionsBuilder.createVmOptions]] with shell debug mode enabled.
 * That path calls [[com.intellij.debugger.engine.DebuggerUtils#getInstance]], which requires an IntelliJ application instance.<br>
 * The IntelliJ test lifecycle plus explicit [[TestApplicationManager]] initialization in setUp` provide that application
 */
@RunWith(classOf[JUnit4])
class SbtShellVmOptionsBuilderTest extends UsefulTestCase {

  private val HiddenDefaultSize = JvmMemorySize.Megabytes(1500)
  private val HiddenDefaultXmxParam = s"-Xmx$HiddenDefaultSize"

  private val RunIdValue = "test-run-id"
  private val runIdOption = s"-D${SpecialSbtVmOptions.IdeaRunIdVmOption}=$RunIdValue"
  private val NoFormatOption = "-Dsbt.log.noformat=true"
  private val JavaOptsEnvVariableName = "JAVA_OPTS"
  private val FallbackParentJavaOpt = "-Dfrom.parent.env=true"
  private val FallbackParentJavaOpts = s"$FallbackParentJavaOpt -Xmx2g"

  private val testNameRule = new TestName

  @Rule def testName: TestName = testNameRule

  override def setUp(): Unit = {
    super.setUp()

    // Init the application instance (see Scaladoc of this class)
    TestApplicationManager.getInstance
  }

  @Test
  def buildVMParameters_WithUserDefinedMaxHeapSizeSmallerThanHiddenDefault_ShouldKeepUserDefinedMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xmx4g",
        "-Xms4g",
        "-Xmx1g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xmx1g"),
        fileJvmOpts = Seq("-Xmx4g", "-Xms4g")
      )
    )
  }

  @Test
  def buildVMParameters_WithUserDefinedMaxHeapSizeGreaterThanHiddenDefault_ShouldKeepUserDefinedMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xmx4g",
        "-Xms4g",
        "-Xmx2g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xmx2g"),
        fileJvmOpts = Seq("-Xmx4g", "-Xms4g")
      )
    )
  }

  @Test
  def buildVMParameters_WithoutMaxHeapSizeConfigured_ShouldPrependHiddenDefaultMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
      ),
      buildParamSeq(
        userJvmOptions = Seq.empty,
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithInitialHeapSizeSmallerThanHiddenDefault_ShouldPrependHiddenDefaultMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xms1g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xms1g"),
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithInitialHeapSizeGreaterThanHiddenDefault_ShouldNotPrependHiddenDefaultMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xms2g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xms2g"),
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithInitialHeapSizeEqualToHiddenDefault_ShouldNotPrependHiddenDefaultMaxHeapSize(): Unit = {
    val hiddenDefaultXmsParam = HiddenDefaultXmxParam.replace("-Xmx", "-Xms")

    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        hiddenDefaultXmsParam,
      ),
      buildParamSeq(
        userJvmOptions = Seq(hiddenDefaultXmsParam),
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithMultipleInitialHeapSizes_ShouldUseLastInitialHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xms2g",
        "-Xms1g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xms2g", "-Xms1g"),
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithFileInitialHeapSizeGreaterThanDefaultAndSettingsInitialHeapSizeSmallerThanDefault_ShouldUseSettingsInitialHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xms2g",
        "-Xms1g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xms1g"),
        fileJvmOpts = Seq("-Xms2g")
      )
    )
  }

  @Test
  def buildVMParameters_WithSettingsInitialHeapSizeGreaterThanDefaultAndSbtInitialHeapSizeSmallerThanDefault_ShouldUseSbtInitialHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xms2g",
        "-Xms1g",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xms2g"),
        fileJvmOpts = Seq.empty,
        sbtOpts = Seq("-Xms1g")
      )
    )
  }

  @Test
  def buildVMParameters_WithInvalidInitialHeapSize_ShouldPrependHiddenDefaultMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xmsinvalid",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xmsinvalid"),
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithInvalidMaxHeapSize_ShouldNotPrependHiddenDefaultMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xmx",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Xmx"),
        fileJvmOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithSbtMaxHeapSize_ShouldNotPrependHiddenDefaultMaxHeapSize(): Unit = {
    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Xmx2g",
      ),
      buildParamSeq(
        userJvmOptions = Seq.empty,
        fileJvmOpts = Seq.empty,
        sbtOpts = Seq("-Xmx2g")
      )
    )
  }

  @Test
  def buildVMParameters_WithSbtVmOptions_ShouldAppendThemAfterJavaOptions(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Dfrom.settings=true",
        "-Dfrom.sbt.opts=true",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Dfrom.settings=true"),
        fileJvmOpts = Seq.empty,
        sbtOpts = Seq("-Dfrom.sbt.opts=true")
      )
    )
  }

  @Test
  def buildVMParameters_WithJavaOptsFromEnvironment_ShouldCollectEnvironmentFileAndSettingsInOrder(): Unit = {
    assertCollectionEquals(
      Seq(
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Dfrom.env=true",
        "-Xmx2g",
        "-Dfrom.file=true",
        "-Dfrom.settings=true",
      ),
      buildParamSeq(
        userJvmOptions = Seq("-Dfrom.settings=true"),
        fileJvmOpts = Seq("-Dfrom.file=true"),
        sbtOpts = Seq.empty,
        userSetEnvironment = Map("JAVA_OPTS" -> "-Dfrom.env=true -Xmx2g")
      )
    )
  }

  @Test
  def buildVMParameters_WithSeparateJvmOptionOperandsInJvmopts_ShouldKeepOperands(): Unit = {
    assertCollectionEquals(
      Seq(
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "--add-exports",
        "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-modules",
        "java.base",
      ),
      buildParamSeq(
        userJvmOptions = Seq.empty,
        fileJvmOpts = Seq(
          "--add-exports java.base/sun.nio.ch=ALL-UNNAMED",
          "--add-modules",
          "java.base"
        ),
        sbtOpts = Seq.empty
      )
    )
  }

  @Test
  def buildVMParameters_WithParentEnvironment_ShouldCollectParentJavaOpts(): Unit = {
    val originalEnvironmentMap = EnvironmentUtil.getEnvironmentMap.asScala.toMap
    val originalJavaOpts = originalEnvironmentMap.get(JavaOptsEnvVariableName)
    val shouldSetJavaOpts = originalJavaOpts.forall(_.isEmpty)

    if (shouldSetJavaOpts) {
      setEnvironmentMap(originalEnvironmentMap + (JavaOptsEnvVariableName -> FallbackParentJavaOpts))
    }

    try {
      val expectedWithoutParentEnvironment = buildParamSeq(
        userJvmOptions = Seq.empty,
        fileJvmOpts = Seq.empty,
        sbtOpts = Seq.empty,
        userSetEnvironment = Map.empty,
        passParentEnvironment = false
      )
      val actualWithParentEnvironment = buildParamSeq(
        userJvmOptions = Seq.empty,
        fileJvmOpts = Seq.empty,
        sbtOpts = Seq.empty,
        userSetEnvironment = Map.empty,
        passParentEnvironment = true
      )
      val actualOptionsFromParentEnvironment = actualWithParentEnvironment.filterNot(expectedWithoutParentEnvironment.contains)

      assertTrue(
        s"Expected parent JAVA_OPTS to add at least one VM option, but actual VM parameters were: $actualWithParentEnvironment",
        actualOptionsFromParentEnvironment.nonEmpty
      )
      if (shouldSetJavaOpts) {
        assertContains(actualWithParentEnvironment, FallbackParentJavaOpt)
      }
    } finally {
      if (shouldSetJavaOpts) {
        setEnvironmentMap(originalEnvironmentMap)
      }
      val restoredEnvironmentMap = EnvironmentUtil.getEnvironmentMap.asScala.toMap
      assertEquals(originalEnvironmentMap, restoredEnvironmentMap)
    }
  }

  @Test
  def createVmOptions_WhenAddPluginCommandIsNotSupported_ShouldAddRunId(): Unit = {
    val data = createVmOptions(addPluginCommandSupported = false)

    assertContains(vmOptions(data), runIdOption)
  }

  @Test
  def createVmOptions_WhenAddPluginCommandIsSupported_ShouldNotAddRunId(): Unit = {
    val data = createVmOptions(addPluginCommandSupported = true)

    assertDoesNotContain(vmOptions(data), runIdOption)
  }

  @Test
  def createVmOptions_WhenShellDebugModeIsDisabled_ShouldNotCreateDebugConnection(): Unit = {
    val data = createVmOptions(shellDebugMode = false)
    val options = vmOptions(data)

    assertEquals(None, data.debugConnection)
    assertDoesNotContain(options, "-Xdebug")
    assertTrue(options.forall(option => !option.startsWith("-agentlib:jdwp=")))
  }

  @Test
  def createVmOptions_WhenShellDebugModeIsEnabled_ShouldAddDebugParametersAndCreateDebugConnection(): Unit = {
    val data = createVmOptions(shellDebugMode = true)
    val options = vmOptions(data)

    assertDoesNotContain(options, "-Xdebug")
    assertTrue(options.exists(_.startsWith("-agentlib:jdwp=transport=dt_socket,address=localhost:")))
    data.debugConnection match {
      case Some(debugConnection) =>
        assertEquals("localhost", debugConnection.getDebuggerHostName)
        assertTrue(debugConnection.getDebuggerAddress.nonEmpty)
      case None =>
        throw new AssertionError("Expected debug connection to be created")
    }
  }

  @Test
  def createVmOptions_ShouldAlwaysAddServerOptionBeforeCollectedVmOptions(): Unit = {
    val data = createVmOptions(addPluginCommandSupported = true)

    assertCollectionEquals(
      Seq(
        "-server",
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
      ),
      vmOptions(data)
    )
  }

  @Test
  def createVmOptions_ShouldIncludeProvidedSbtVmOptions(): Unit = {
    val data = createVmOptions(
      addPluginCommandSupported = true,
      sbtVmOptions = Seq("-Dglobal.option=true", "-Dshell.option=true")
    )

    assertCollectionEquals(
      Seq(
        "-server",
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Dglobal.option=true",
        "-Dshell.option=true",
      ),
      vmOptions(data)
    )
  }

  @Test
  def createVmOptions_WithNoShareSbtOption_ShouldAddSeparateJvmOptions(): Unit = {
    val data = createVmOptions(
      addPluginCommandSupported = true,
      sbtVmOptions = Seq(
        "-Dsbt.global.base=project/.sbtboot",
        "-Dsbt.boot.directory=project/.boot",
        "-Dsbt.ivy.home=project/.ivy"
      )
    )

    assertCollectionEquals(
      Seq(
        "-server",
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Dsbt.global.base=project/.sbtboot",
        "-Dsbt.boot.directory=project/.boot",
        "-Dsbt.ivy.home=project/.ivy",
      ),
      vmOptions(data)
    )
  }

  @Test
  def createVmOptions_WithTimingsSbtOption_ShouldAddSeparateJvmOptions(): Unit = {
    val data = createVmOptions(
      addPluginCommandSupported = true,
      sbtVmOptions = Seq(
        "-Dsbt.task.timings=true",
        "-Dsbt.task.timings.on.shutdown=true"
      )
    )

    assertCollectionEquals(
      Seq(
        "-server",
        HiddenDefaultXmxParam,
        "-Dsbt.supershell=false",
        "-Djdk.console=java.base",
        "-Dsbt.task.timings=true",
        "-Dsbt.task.timings.on.shutdown=true",
      ),
      vmOptions(data)
    )
  }

  @Test
  def createVmOptions_WithOldShellOnWindows_ShouldAddNoFormatOption(): Unit = {
    val data = createVmOptions(addPluginCommandSupported = true, useNewShell = false, isWindows = Some(true))

    assertContains(vmOptions(data), NoFormatOption)
  }

  @Test
  def createVmOptions_WithOldShellOutsideWindows_ShouldNotAddNoFormatOption(): Unit = {
    val data = createVmOptions(addPluginCommandSupported = true, useNewShell = false, isWindows = Some(false))

    assertDoesNotContain(vmOptions(data), NoFormatOption)
  }

  @Test
  def createVmOptions_WithNewShell_ShouldNotAddNoFormatOption(): Unit = {
    val data = createVmOptions(addPluginCommandSupported = true, useNewShell = true, isWindows = Some(true))

    assertDoesNotContain(vmOptions(data), NoFormatOption)
  }

  private def buildParamSeq(
    userJvmOptions: Seq[String],
    fileJvmOpts: Seq[String]
  ): Seq[String] = {
    buildParamSeq(
      userJvmOptions = userJvmOptions,
      fileJvmOpts = fileJvmOpts,
      sbtOpts = Seq.empty
    )
  }

  private def buildParamSeq(
    userJvmOptions: Seq[String],
    fileJvmOpts: Seq[String],
    sbtOpts: Seq[String]
  ): Seq[String] = {
    buildParamSeq(
      userJvmOptions = userJvmOptions,
      fileJvmOpts = fileJvmOpts,
      sbtOpts = sbtOpts,
      userSetEnvironment = Map.empty
    )
  }

  private def buildParamSeq(
    userJvmOptions: Seq[String],
    fileJvmOpts: Seq[String],
    sbtOpts: Seq[String],
    userSetEnvironment: Map[String, String]
  ): Seq[String] = {
    buildParamSeq(
      userJvmOptions = userJvmOptions,
      fileJvmOpts = fileJvmOpts,
      sbtOpts = sbtOpts,
      userSetEnvironment = userSetEnvironment,
      passParentEnvironment = false
    )
  }

  private def buildParamSeq(
    userJvmOptions: Seq[String],
    fileJvmOpts: Seq[String],
    sbtOpts: Seq[String],
    userSetEnvironment: Map[String, String],
    passParentEnvironment: Boolean
  ): Seq[String] = {
    import org.jetbrains.sbt.PathTestUtil.tempPathReleasable

    Using.resource(Files.createTempDirectory(s"sbtShellVmOptionsBuilderTest-${testName.getMethodName}")) { workingDir =>
      buildParamSeqInDir(userJvmOptions, fileJvmOpts, workingDir, sbtOpts, userSetEnvironment, passParentEnvironment)
    }
  }

  private def buildParamSeqInDir(
    userJvmOptions: Seq[String],
    fileJvmOpts: Seq[String],
    workingDir: Path,
    sbtOpts: Seq[String],
    userSetEnvironment: Map[String, String],
    passParentEnvironment: Boolean
  ): Seq[String] = {
    writeJvmOptsToFileInDir(fileJvmOpts, workingDir)
    val settings = createExecutionSettings(
      userJvmOptions = userJvmOptions,
      userSetEnvironment = userSetEnvironment,
      passParentEnvironment = passParentEnvironment
    )
    SbtShellVmOptionsBuilder.buildVMParameters(settings, workingDir, sbtOpts)
  }

  private def createVmOptions(
    addPluginCommandSupported: Boolean = true,
    shellDebugMode: Boolean = false,
    sbtVmOptions: Seq[String] = Seq.empty,
    useNewShell: Boolean = true,
    isWindows: Option[Boolean] = None
  ): SbtShellVmOptionsData = {
    import org.jetbrains.sbt.PathTestUtil.tempPathReleasable

    Using.resource(Files.createTempDirectory(s"sbtShellVmOptionsBuilderTest-${testName.getMethodName}")) { workingDir =>
      val settings = createExecutionSettings(
        userJvmOptions = Seq.empty,
        shellDebugMode = shellDebugMode
      )
      val builder = isWindows
        .map(new SbtShellVmOptionsBuilder(_))
        .getOrElse(new SbtShellVmOptionsBuilder())
      builder.createVmOptions(
        settings,
        workingDir,
        addPluginCommandSupported,
        SbtShellRunId(RunIdValue),
        sbtVmOptions,
        useNewShell
      )
    }
  }

  private def createExecutionSettings(
    userJvmOptions: Seq[String],
    shellDebugMode: Boolean = false,
    userSetEnvironment: Map[String, String] = Map.empty,
    passParentEnvironment: Boolean = false
  ): SbtExecutionSettings = new SbtExecutionSettings(
    realProjectPath = null,
    vmExecutable = null,
    vmOptions = userJvmOptions,
    sbtOptions = SbtExecutionSettings.SbtOptions.empty,
    hiddenDefaultMaxHeapSize = HiddenDefaultSize,
    customLauncher = null,
    customSbtStructureFile = null,
    jdk = null,
    resolveClassifiers = false,
    resolveSbtClassifiers = false,
    useShellForImport = false,
    shellDebugMode = shellDebugMode,
    preferScala2 = true,
    userSetEnvironment = userSetEnvironment,
    passParentEnvironment = passParentEnvironment,
    useSeparateCompilerOutputPaths = false,
    separateProdTestSources = false,
    generateManagedSourcesDuringProjectSync = true,
    sbtVersion = SbtVersion.Latest.Sbt_1
  )

  private def writeJvmOptsToFileInDir(jvmOpts: Seq[String], workingDir: Path): Unit = {
    if (jvmOpts.nonEmpty) {
      val jvmOptsFile = workingDir.resolve(".jvmopts")
      Files.writeString(jvmOptsFile, jvmOpts.mkString("\n"))
    }
  }

  private def vmOptions(data: SbtShellVmOptionsData): Seq[String] =
    data.vmOptions.getParameters.asScala.toSeq

  private def setEnvironmentMap(environment: Map[String, String]): Unit = {
    val value: Supplier[java.util.Map[String, String]] = () => {
      environment.asJava
    }
    EnvironmentUtil.setEnvironmentLoader(value)
  }

  private def assertContains(actual: Seq[String], expected: String): Unit =
    assertTrue(s"Expected $actual to contain $expected", actual.contains(expected))

  private def assertDoesNotContain(actual: Seq[String], expected: String): Unit =
    assertFalse(s"Expected $actual not to contain $expected", actual.contains(expected))
}
