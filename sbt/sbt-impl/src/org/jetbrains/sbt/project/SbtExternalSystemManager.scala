package org.jetbrains.sbt
package project

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, ProjectSystemId}
import com.intellij.openapi.externalSystem.util._
import com.intellij.openapi.externalSystem.{ExternalSystemConfigurableAware, ExternalSystemManager}
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkType, JdkUtil, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Pair, SystemInfo}
import com.intellij.util.Function
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.plugins.scala.extensions.{RichFile, invokeAndWait}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.project.structure.SbtOpts
import org.jetbrains.sbt.settings.{SbtExternalSystemConfigurable, SbtSettings}

import java.io.File
import scala.jdk.CollectionConverters._

class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings, SbtLocalSettings, SbtExecutionSettings]
    with ExternalSystemConfigurableAware
    with AutoImportAwareness {

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    classpath.add(jarWith[org.jetbrains.sbt.structure.XmlSerializer[_]])
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[scala.xml.Node])

    parameters.getVMParametersList.addProperty(
      ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, SbtProjectSystem.Id.getId)

    parameters.getVMParametersList.addProperty(
      PathManager.PROPERTY_LOG_PATH, PathManager.getLogPath)
  }

  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getSettingsProvider: Function[Project, SbtSettings] = SbtSettings.getInstance _

  override def getLocalSettingsProvider: Function[Project, SbtLocalSettings] = SbtLocalSettings.getInstance _

  override def getExecutionSettingsProvider: Function[Pair[Project, String], SbtExecutionSettings] =
    SbtExternalSystemManager.executionSettingsFor _

  override def getProjectResolverClass: Class[SbtProjectResolver] = classOf[SbtProjectResolver]

  override def getTaskManagerClass: Class[SbtTaskManager] = classOf[SbtTaskManager]

  override def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()

  override def getConfigurable(project: Project): Configurable = new SbtExternalSystemConfigurable(project)
}

object SbtExternalSystemManager {

  private val Log = Logger.getInstance(classOf[SbtExternalSystemManager])

  def executionSettingsFor(project: Project, path: String): SbtExecutionSettings = {
    import scala.jdk.CollectionConverters._

    val settings = SbtSettings.getInstance(project)
    val settingsState = settings.getState

    val linkedProjectSettings = settings.getLinkedProjectSettings(path)
    val projectSettings = Option(linkedProjectSettings).getOrElse(SbtProjectSettings.default)

    val customLauncher = settingsState.customLauncherEnabled.option(settingsState.customLauncherPath).map(_.toFile)
    val customSbtStructureFile = settingsState.customSbtStructurePath.nonEmpty.option(settingsState.customSbtStructurePath.toFile)

    val realProjectPath = Option(projectSettings.getExternalProjectPath).getOrElse(path)
    val projectJdkName = bootstrapJdk(project, projectSettings)
    val vmExecutable = getVmExecutable(projectJdkName, settingsState)
    val jreHome = vmExecutable.parent.flatMap(_.parent)
    val vmOptions = getVmOptions(settingsState, jreHome)
    val environment = Map.empty ++ getAndroidEnvironmentVariables(projectJdkName)
    val sbtOptions = SbtOpts.combineOptionsWithArgs(settings.sbtOptions)

    new SbtExecutionSettings(
      realProjectPath,
      vmExecutable,
      vmOptions,
      sbtOptions,
      SbtSettings.hiddenDefaultMaxHeapSize,
      environment,
      customLauncher,
      customSbtStructureFile,
      projectJdkName,
      projectSettings.resolveClassifiers,
      projectSettings.resolveSbtClassifiers,
      projectSettings.useSbtShellForImport,
      projectSettings.enableDebugSbtShell,
      projectSettings.preferScala2,
      projectSettings.groupProjectsFromSameBuild,
      settingsState.sbtEnvironment.asScala.toMap,
      settingsState.sbtPassParentEnvironment,
      projectSettings.insertProjectTransitiveDependencies,
      projectSettings.useSeparateCompilerOutputPaths
    )
  }

  /** Choose a jdk for imports. This is then only used when no overriding information is available from sbt definition.
   * SbtProjectResolver figures out that part
   */
  private def bootstrapJdk(project: Project, importSettings: SbtProjectSettings) = {
    // either what was set in previous import, or default from Project Structure defaults
    val jdkInProject = Option(ProjectRootManager.getInstance(project).getProjectSdk).map(_.getName)
    // setting used *only* for initial import
    val jdkInImportSettings = importSettings.jdkName
    // use setting from initial import only when there is no other information
    val result = jdkInProject.orElse(jdkInImportSettings)
    Log.debug(s"""bootstrapJdk: $result${if (result.isEmpty) "" else s" (from project: ${jdkInProject.isDefined})"}""")
    result
  }

  private def getVmExecutable(projectJdkName: Option[String], settings: SbtSettings.State): File = {
    val jdkTable = ProjectJdkTable.getInstance()

    val customPath = settings.customVMPath
    val customVmExecutable =
      if (settings.customVMEnabled && JdkUtil.checkForJre(customPath)) {
        Log.debug(s"Using Java from custom VM path: $customPath")

        @NonNls val javaExe = if (SystemInfo.isWindows) "java.exe" else "java"
        Some(new File(customPath) / "bin" / javaExe)
      }
      else None

    val realExe = customVmExecutable
      .orElse {
        val projectJdkFound = projectJdkName.safeMap(jdkTable.findJdk)
        projectJdkFound
          .map { sdk =>
            Log.debug(s"Using Java project JDK: $sdk")

            sdk.getSdkType match {
              case sdkType: JavaSdkType =>
                new File(sdkType.getVMExecutablePath(sdk))
              case _ =>
                // ugh
                throw new ExternalSystemException(SbtBundle.message("sbt.import.noProjectJvmFound"))
            }
          }
      }
      .orElse {
        //automatically detect JDK if none is defined
        invokeAndWait {
          val sdk = SbtProcessJdkGuesser.findJdkWithSuitableVersion(jdkTable)
          if (sdk.sdk.isEmpty) {
            Log.debug("Preconfigure JDK table for SBT import")
            SbtProcessJdkGuesser.preconfigureJdkForSbt(jdkTable)
          }
        }

        val suitableSdk = SbtProcessJdkGuesser.findJdkWithSuitableVersion(jdkTable)
        val autoDetectedSdk = suitableSdk.sdk
          //if no suitable sdj >= 8 found, take any JDK, and hope that sbt import will work
          .orElse(suitableSdk.allSdkSorted.lastOption)

        autoDetectedSdk.map { sdk =>
          Log.debug(s"Using Java from best auto-detected JDK: $sdk")

          new File(JavaSdk.getInstance().getVMExecutablePath(sdk))
        }
      }
      .getOrElse {
        throw new ExternalSystemException(SbtBundle.message("sbt.import.noCustomJvmFound"))
      }

    realExe
  }

  private def getAndroidEnvironmentVariables(projectJdkName: Option[String]): Map[String, String] =
    projectJdkName
      .flatMap(name => Option(ProjectJdkTable.getInstance().findJdk(name)))
      .map(SbtEnvironmentVariablesProvider.computeAdditionalVariables)
      .getOrElse(Map.empty)

  private def getVmOptions(settings: SbtSettings.State, jreHome: Option[File]): Seq[String] = {
    @NonNls val userOptions = settings.vmParameters.split("\\s+").toSeq.filter(_.nonEmpty)

    @NonNls val maxHeapSizeString = settings.maximumHeapSize.trim
    @NonNls val maxHeapOptions =
      if (maxHeapSizeString.nonEmpty) {
        val maxHeapSize =
          JvmMemorySize.parse(maxHeapSizeString + "M")
            .orElse(JvmMemorySize.parse(maxHeapSizeString))
            .map(_.toString)
            .getOrElse(maxHeapSizeString + "M")

        Seq(s"-Xmx$maxHeapSize")
      } else Seq.empty

    val givenOptions = maxHeapOptions ++ userOptions

    getVmOptions(givenOptions, jreHome)
  }

  def getVmOptions(givenOptions: Seq[String], jreHome: Option[File]): Seq[String] = {
    import DefaultOptions._
    val ideaProxyOptions = proxyOptions { optName => !givenOptions.exists(_.startsWith(optName)) }

    val allOptions = ideaProxyOptions ++ givenOptions

    val ideaInstallRoot = PathManager.getHomePath

    allOptions
      .addDefaultOption(ideaManaged.key, ideaManaged.value)
      .addDefaultOption(fileEncoding.key, fileEncoding.value)
      .addDefaultOption(ideaInstallationRootKey, ideaInstallRoot)
      .addPermSize(jreHome)
  }


  /** @param select Allow only options that pass this filter on option name */
  private def proxyOptions(select: String => Boolean): Seq[String] = {

    val http = HttpConfigurable.getInstance
    val jvmArgs = http
      .getJvmProperties(false, null)
      .asScala.iterator
      .map { pair => (pair.first, pair.second)}
      .toSeq

    // TODO workaround for IDEA-186551 -- remove when fixed in core
    val nonProxyHosts =
      if (!StringUtil.isEmpty(http.PROXY_EXCEPTIONS) && (http.USE_HTTP_PROXY || http.USE_PROXY_PAC)) {
        val hosts = http.PROXY_EXCEPTIONS.split(",")
        if (hosts.nonEmpty) {
          val hostString = hosts.map(_.trim).mkString("|")
          Seq(("http.nonProxyHosts", hostString))
        } else Seq.empty
      } else Seq.empty

    (jvmArgs ++ nonProxyHosts)
      .collect { case (name,value) if select(name) => s"-D$name=$value" }
  }

  private implicit class OptionsOps(options: Seq[String]) {
    def addPermSize(jreHome: Option[File]): Seq[String] = {
      import DefaultOptions.maxPermSize

      // use no MaxPermSize param if we know jdk version is >= 8 or user set it anyway
      val withoutPermSize = for {
        home <- jreHome
        if ! hasOption(maxPermSize.key)
        jreVersion <- Option(JdkVersionDetector.getInstance().detectJdkVersionInfo(home.getAbsolutePath))
        if jreVersion.version.feature >= 8
      } yield options

      // add permsize by default
      withoutPermSize.getOrElse(addDefaultOption(maxPermSize.key, maxPermSize.value))
    }

    def addDefaultOption(prefix: String, value: String): Seq[String] =
      if (hasOption(prefix)) options
      else options :+ s"$prefix=$value"

    private def hasOption(prefix: String) =
      options.exists(_.startsWith(s"$prefix="))
  }

  private[project] object DefaultOptions {
    final case class JvmOption(@NonNls key: String, @NonNls value: String)

    val fileEncoding: JvmOption = JvmOption("-Dfile.encoding", "UTF-8")
    val maxPermSize: JvmOption = JvmOption("-XX:MaxPermSize", "256M")

    /** custom option to signal sbt instance is run from idea. */
    val ideaManaged: JvmOption = JvmOption("-Didea.managed", "true")

    val ideaInstallationRootKey = "-Didea.installation.dir"
  }

}
