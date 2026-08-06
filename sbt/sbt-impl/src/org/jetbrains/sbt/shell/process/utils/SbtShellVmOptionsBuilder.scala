package org.jetbrains.sbt.shell.process.utils

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.{ParametersList, RemoteConnection}
import com.intellij.util.system.OS
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbt.JvmMemorySize
import org.jetbrains.sbt.process.options.SbtProcessOptionsResolver
import org.jetbrains.sbt.project.settings.SbtExecutionSettings
import org.jetbrains.sbt.shell.SbtProcessManager.SbtShellVmOptionsData
import org.jetbrains.sbt.shell.process.utils.SbtShellVmOptionsBuilder.buildVMParameters

import java.nio.file.Path
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

private[shell] final class SbtShellVmOptionsBuilder @TestOnly private[utils](
  isWindows: Boolean
) {

  def this() = this(OS.CURRENT == OS.Windows)

  def createVmOptions(
    sbtSettings: SbtExecutionSettings,
    workingDir: Path,
    addPluginCommandSupported: Boolean,
    runId: SbtShellRunId,
    sbtVmOptions: Seq[String],
    useNewShell: Boolean
  ): SbtShellVmOptionsData = {
    val vmParams = new ParametersList

    // don't add runId when using addPluginSbtFile command
    if (!addPluginCommandSupported)
      vmParams.add(s"-D${SpecialSbtVmOptions.IdeaRunIdVmOption}=${runId.value}")

    // TODO: handle conflicts with user-provided JDWP options more gracefully.
    //  Investigation: adding two -agentlib:jdwp=... options makes JBR 21 fail during VM initialization with "Cannot load this JVM TI agent twice".
    //  This can happen when shell debug mode is enabled and the user also provides an sbt -jvm-debug option via SBT_OPTS/.sbtopts/settings, because those options are appended later.
    //  Decide which option should take precedence, deduplicate accordingly, and report a warning to the user.
    val debugConnection = if (sbtSettings.shellDebugMode) Some(addDebugParameters(vmParams)) else None

    vmParams.add("-server")

    val allOpts = buildVMParameters(sbtSettings, workingDir, sbtVmOptions)
    vmParams.addAll(allOpts.asJava)

    // For details see: https://youtrack.jetbrains.com/issue/SCL-13293#focus=streamItem-27-3323121.0-0
    // When the new shell is enabled and TerminalExecutionConsole is used, the colors can be enabled again on Windows
    if (isWindows && !useNewShell) {
      vmParams.add("-Dsbt.log.noformat=true")
    }

    SbtShellVmOptionsData(
      vmParams,
      debugConnection
    )
  }

  /**
   * Add debug parameters to the ParametersList and create a remote connection
   *
   * @note It seems like [[RemoteConnection]] class does not represent any state or real connection,
   *       it merely stores some data to instantiate this connection.
   *       So it should be safe to return it as data here
   */
  private def addDebugParameters(vmParams: ParametersList): RemoteConnection = {
    val host = "localhost"
    val port = DebuggerUtils.getInstance.findAvailableDebugAddress(true)

    val shellDebugProperties = s"-agentlib:jdwp=transport=dt_socket,address=$host:$port,suspend=n,server=y"
    vmParams.replaceOrPrepend("-agentlib:jdwp=", shellDebugProperties)

    new RemoteConnection(true, host, port, false)
  }
}

object SbtShellVmOptionsBuilder {

  private val HardcodedVmOptions: Seq[String] = List(
    "-Dsbt.supershell=false",
    // SCL-22878: keep sbt compatible with System.console() changes on JDK 21+.
    // Added unconditionally because older JDKs are expected to tolerate this unused system property.
    "-Djdk.console=java.base"
  )

  private val XmxPrefix = "-Xmx"
  private val XmsPrefix = "-Xms"

  private[shell] def buildVMParameters(
    settings: SbtExecutionSettings,
    workingDir: Path,
    sbtOpts: Seq[String]
  ): Seq[String] = {
    val javaOptions = SbtProcessOptionsResolver.resolveJavaOptions(
      workingDir,
      settings.vmOptions,
      EnvironmentVariablesData.create(settings.userSetEnvironment.asJava, settings.passParentEnvironment)
    )
    val jvmOpts: Seq[String] = HardcodedVmOptions ++
      javaOptions ++
      sbtOpts

    val extraXmx = extraXmxOptionIfNeeded(settings, jvmOpts)
    extraXmx.toSeq ++ jvmOpts
  }

  private def extraXmxOptionIfNeeded(settings: SbtExecutionSettings, jvmOpts: Seq[String]): Option[String] = {
    // Treat any user-provided -Xmx* option as explicit max-heap intent, even if the JVM later rejects it as malformed.
    // Adding the hidden default before a malformed user option would not make startup succeed, because the bad option remains.
    // TODO: Replace this check with an explicit max-heap-configured predicate that also recognizes representative
    //  -XX:MaxRAM... options, and cover them in SbtShellVmOptionsBuilderTest.
    //  Investigation: valid JVM options such as -XX:MaxRAMPercentage, -XX:MaxRAMFraction, and -XX:MaxRAM
    //  express max-heap sizing without the -Xmx prefix.
    //  The current builder still prepends the hidden -Xmx1500M in that case,
    //  which defeats the user's dynamic max-heap sizing intent because an explicit -Xmx normally takes precedence
    //  over those MaxRAM-derived heap sizing options.
    //  JDK context: -XX:MaxRAMPercentage was added in JDK 10 under JDK-8186248 and backported to
    //  JDK 8u191 with container support; -XX:MaxRAMFraction is the older, deprecated fraction form.
    val hasXmx = jvmOpts.exists(_.startsWith(XmxPrefix))

    def minMaxHeapSize: Option[JvmMemorySize] =
      findExistingXms(jvmOpts)

    def xmxNotNeeded: Boolean =
      minMaxHeapSize.exists(_ >= settings.hiddenDefaultMaxHeapSize)

    val hasXmxOrNotNeeded = hasXmx || xmxNotNeeded
    val needExplicitXmx = !hasXmxOrNotNeeded
    if (needExplicitXmx) {
      val xmxValue = settings.hiddenDefaultMaxHeapSize.sizeString
      Option(s"$XmxPrefix$xmxValue")
    }
    else
      None
  }

  private def findExistingXms(jvmOpts: Seq[String]): Option[JvmMemorySize] = {
    val existingXms = jvmOpts.reverseIterator.find(_.startsWith(XmsPrefix))
    val existingSize = existingXms.map(_.drop(XmsPrefix.length))
    existingSize.flatMap(JvmMemorySize.parse)
  }
}
