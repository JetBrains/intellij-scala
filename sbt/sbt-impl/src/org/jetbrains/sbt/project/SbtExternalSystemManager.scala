package org.jetbrains.sbt
package project

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, ProjectSystemId}
import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.externalSystem.{ExternalSystemConfigurableAware, ExternalSystemManager}
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkType, JdkUtil, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.{Pair, SystemInfo}
import com.intellij.util.Function
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.{NonNls, VisibleForTesting}
import org.jetbrains.jps.incremental.scala.remote.SerializablePath
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.plugins.scala.extensions.{PathExt, invokeAndWait}
import org.jetbrains.sbt.SbtUtil.{defaultLauncherPath, detectSbtVersion}
import org.jetbrains.sbt.process.options.SbtProcessOptionsResolver
import org.jetbrains.sbt.project.settings.*
import org.jetbrains.sbt.settings.{SbtExternalSystemConfigurable, SbtSettings}
import org.jetbrains.sbt.{SbtBundle, SbtUtil}

import java.nio.file.Path
import scala.annotation.nowarn

class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtProjectSettingsListener, SbtSettings, SbtLocalSettings, SbtExecutionSettings]
    with ExternalSystemConfigurableAware
    with AutoImportAwareness {

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters): Unit = {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    //    classpath.add(jarWith[org.jetbrains.sbt.structure.XmlSerializer[?]].toCanonicalPath.toString)
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[scala.xml.Node])

    parameters.getVMParametersList.addProperty(
      ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, SbtProjectSystem.Id.getId)

    parameters.getVMParametersList.addProperty(
      PathManager.PROPERTY_LOG_PATH, PathManager.getLogPath)
  }

  override def getSystemId: ProjectSystemId = SbtProjectSystem.Id

  override def getSettingsProvider: Function[Project, SbtSettings] = SbtSettings.getInstance

  override def getLocalSettingsProvider: Function[Project, SbtLocalSettings] = SbtLocalSettings.getInstance

  override def getExecutionSettingsProvider: Function[Pair[Project, String], SbtExecutionSettings] =
    tup => SbtExternalSystemManager.executionSettingsFor(tup.first, tup.second)

  override def getProjectResolverClass: Class[SbtProjectResolver] = classOf[SbtProjectResolver]

  override def getTaskManagerClass: Class[SbtTaskManager] = classOf[SbtTaskManager]

  override def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()

  override def getConfigurable(project: Project): Configurable = new SbtExternalSystemConfigurable(project)
}

object SbtExternalSystemManager {

  private val Log = Logger.getInstance(classOf[SbtExternalSystemManager])

  @RequiresBackgroundThread
  def executionSettingsFor(project: Project): SbtExecutionSettings = {
    val workingDirPath = SbtUtil.getWorkingDirPath(project)
    executionSettingsFor(project, workingDirPath)
  }

  /**
   * NOTE: the method requires BGT mainly due to JDK guessing logic under the hood, which can be time-consuming.<br>
   * Ideally, the lifecycle of entities/settings should be reviewed in order the JDK guessing is done
   * not during these settings extractions, but at some point where the BGT is available.
   * And when the [[executionSettingsFor]] would be called on EDT we would rely on the JDK to be already initialised.
   */
  @RequiresBackgroundThread
  def executionSettingsFor(project: Project, path: String): SbtExecutionSettings = {
    import scala.jdk.CollectionConverters.*

    val settings = SbtSettings.getInstance(project)
    val settingsState = settings.getState

    val linkedProjectSettings = settings.getLinkedProjectSettings(path)
    val projectSettings = Option(linkedProjectSettings).getOrElse(SbtProjectSettings.default)

    val customLauncher = Option(settingsState.customLauncherPath).map(Path.of(_))
    val customSbtStructureFile = Option(settingsState.customSbtStructurePath).filterNot(StringUtils.isBlank).map(Path.of(_))

    val realProjectPath = Option(projectSettings.getExternalProjectPath).getOrElse(path)

    val sbtLauncher = customLauncher.getOrElse(defaultLauncherPath)
    val projectRoot = {
      val file = Path.of(realProjectPath)
      if (file.isDirectory) file else file.getParent
    }
    val sbtVersion = detectSbtVersion(projectRoot, sbtLauncher)

    val projectJdkName = bootstrapJdk(project, projectSettings)
    val vmExecutable = getVmExecutable(project, projectJdkName, settingsState, sbtVersion)
    val jreHome = Option(vmExecutable.getParent).flatMap(p => Option(p.getParent))
    val vmOptions = getVmOptions(settingsState, jreHome, projectSettings.separateProdAndTestSources)
    val parsedSbtOptions = SbtProcessOptionsResolver.parseSbtOptionsFromSettings(settings.sbtOptions)

    new SbtExecutionSettings(
      realProjectPath = realProjectPath,
      vmExecutable = SerializablePath(vmExecutable),
      vmOptions = vmOptions,
      sbtOptions = SbtExecutionSettings.SbtOptions(parsedSbtOptions.options, parsedSbtOptions.malformedOptions),
      hiddenDefaultMaxHeapSize = SbtSettings.hiddenDefaultMaxHeapSize,
      customLauncher = customLauncher.map(SerializablePath(_)),
      customSbtStructureFile = customSbtStructureFile.map(SerializablePath(_)),
      jdk = projectJdkName,
      resolveClassifiers = projectSettings.resolveClassifiers,
      resolveSbtClassifiers = projectSettings.resolveSbtClassifiers,
      useShellForImport = projectSettings.useSbtShellForImport,
      shellDebugMode = projectSettings.enableDebugSbtShell,
      preferScala2 = projectSettings.preferScala2,
      userSetEnvironment = settingsState.sbtEnvironment.asScala.toMap,
      passParentEnvironment = settingsState.sbtPassParentEnvironment,
      useSeparateCompilerOutputPaths = projectSettings.useSeparateCompilerOutputPaths,
      separateProdTestSources = projectSettings.separateProdAndTestSources,
      generateManagedSourcesDuringProjectSync = projectSettings.generateManagedSourcesDuringProjectSync,
      sbtVersion = sbtVersion
    )
  }

  /** Choose a jdk for imports. This is then only used when no overriding information is available from sbt definition.
   * SbtProjectResolver figures out that part
   */
  private def bootstrapJdk(project: Project, importSettings: SbtProjectSettings): Option[String] = {
    // either what was set in previous import, or default from Project Structure defaults
    val jdkInProject = Option(ProjectRootManager.getInstance(project).getProjectSdk).map(_.getName)
    // setting used *only* for initial import
    val jdkInImportSettings = importSettings.jdkName
    // use setting from initial import only when there is no other information
    val result = jdkInProject.orElse(jdkInImportSettings)
    Log.debug(s"""bootstrapJdk: $result${if (result.isEmpty) "" else s" (from project: ${jdkInProject.isDefined})"}""")
    result
  }

  @RequiresBackgroundThread
  private def getVmExecutable(project: Project, projectJdkName: Option[String], settings: SbtSettings.State, sbtVersion: SbtVersion): Path = {
    val jdkTable = ProjectJdkTable.getInstance(project)

    val customVmExecutable = getCustomJvmPath(settings)
    val orProjectJdk = customVmExecutable.orElse(getProjectJdkPath(projectJdkName, jdkTable))
    val orAutoDetect = orProjectJdk.orElse(getAutoDetectJdkPath(project, sbtVersion, jdkTable))
    orAutoDetect.getOrElse {
      throw new ExternalSystemException(SbtBundle.message("sbt.import.noCustomJvmFound"))
    }
  }

  private def getCustomJvmPath(settings: SbtSettings.State): Option[Path] = {
    Option(settings.customVMPath)
      .map(Path.of(_))
      .filter(JdkUtil.checkForJre)
      .map: customPath =>
        Log.debug(s"Using Java from custom VM path: $customPath")
        @NonNls val javaExe = if SystemInfo.isWindows then "java.exe" else "java"
        customPath / "bin" / javaExe
  }

  private def getProjectJdkPath(projectJdkName: Option[String], jdkTable: ProjectJdkTable): Option[Path] = {
    val projectJdkFound = projectJdkName
      .safeMap(jdkTable.findJdk)
      .filter(jdk => JdkUtil.checkForJdk(jdk.getHomePath)): @nowarn("cat=deprecation")
    projectJdkFound.map { sdk =>
      Log.debug(s"Using Java project JDK: $sdk")

      sdk.getSdkType match {
        case sdkType: JavaSdkType =>
          Path.of(sdkType.getVMExecutablePath(sdk))
        case _ =>
          // ugh
          throw new ExternalSystemException(SbtBundle.message("sbt.import.noProjectJvmFound"))
      }
    }
  }

  @VisibleForTesting
  private[project] trait AutoDetectJdkProvider {
    def findJdkWithSuitableVersion(jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): SbtProcessJdkGuesser.SdkCandidate

    def preconfigureJdkForSbt(project: Project, jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): Unit
  }

  @VisibleForTesting
  private[project] object SbtProcessAutoDetectJdkProvider extends AutoDetectJdkProvider {
    override def findJdkWithSuitableVersion(jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): SbtProcessJdkGuesser.SdkCandidate =
      SbtProcessJdkGuesser.findJdkWithSuitableVersion(jdkTable, sbtVersion)

    override def preconfigureJdkForSbt(project: Project, jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): Unit =
      SbtProcessJdkGuesser.preconfigureJdkForSbt(project, jdkTable, sbtVersion)
  }

  @RequiresBackgroundThread
  private def getAutoDetectJdkPath(project: Project, sbtVersion: SbtVersion, jdkTable: ProjectJdkTable): Option[Path] =
    getAutoDetectJdkPath(project, sbtVersion, jdkTable, SbtProcessAutoDetectJdkProvider)

  @RequiresBackgroundThread
  private[project] def getAutoDetectJdkPath(
    project: Project,
    sbtVersion: SbtVersion,
    jdkTable: ProjectJdkTable,
    autoDetectJdkProvider: AutoDetectJdkProvider,
  ): Option[Path] = {
    //automatically detect JDK if none is defined
    val suitableSdk = autoDetectJdkProvider.findJdkWithSuitableVersion(jdkTable, sbtVersion)
    val sdk = suitableSdk.sdk
      .orElse {
        preconfigureJdkTableForSbtImportIfAllowed(project, jdkTable, sbtVersion, autoDetectJdkProvider)
        val suitableSdk2 = autoDetectJdkProvider.findJdkWithSuitableVersion(jdkTable, sbtVersion)
        suitableSdk2.sdk
      }
      //if no suitable sdk >= 8 found, take any JDK, and hope that sbt import will work
      .orElse(suitableSdk.allSdkSorted.lastOption)

    sdk.map { sdk =>
      Log.debug(s"Using Java from best auto-detected JDK: $sdk")

      Path.of(JavaSdk.getInstance().getVMExecutablePath(sdk))
    }
  }

  @RequiresBackgroundThread
  private def preconfigureJdkTableForSbtImportIfAllowed(
    project: Project,
    jdkTable: ProjectJdkTable,
    sbtVersion: SbtVersion,
    autoDetectJdkProvider: AutoDetectJdkProvider,
  ): Unit = {
    val application = ApplicationManager.getApplication
    if (application.isReadAccessAllowed) {
      Log.debug("Skip preconfiguring JDK table for SBT import because a read action is already active")
    } else {
      invokeAndWait {
        preconfigureJdkTableForSbtImport(project, jdkTable, sbtVersion, autoDetectJdkProvider)
      }
    }
  }

  private def preconfigureJdkTableForSbtImport(
    project: Project,
    jdkTable: ProjectJdkTable,
    sbtVersion: SbtVersion,
    autoDetectJdkProvider: AutoDetectJdkProvider,
  ): Unit = {
    Log.debug("Preconfigure JDK table for SBT import")
    autoDetectJdkProvider.preconfigureJdkForSbt(project, jdkTable, sbtVersion)
  }

  private def getVmOptions(
    settings: SbtSettings.State,
    jreHome: Option[Path],
    separateProdAndTestSources: Boolean
  ): Seq[String] = {
    @NonNls val userOptions = settings.vmParameters.split("\\s+").toSeq.filter(_.nonEmpty)

    @NonNls val maxHeapSizeString = settings.maximumHeapSize
    @NonNls val maxHeapOptions =
      if (maxHeapSizeString != null) {
        val maxHeapSize =
          JvmMemorySize.parse(s"${maxHeapSizeString}M")
            .map(_.toString)
            .getOrElse(s"${maxHeapSizeString}M")

        Seq(s"-Xmx$maxHeapSize")
      } else Seq.empty

    val groupingWithQualifiedNamesEnabled = Seq("-Dgrouping.with.qualified.names.enabled=true")
    val prodTestSeparationEnabledEnabled = Seq(s"-Dseparate.prod.test.sources.enabled=$separateProdAndTestSources")

    val givenOptions = maxHeapOptions ++ groupingWithQualifiedNamesEnabled ++ prodTestSeparationEnabledEnabled ++ userOptions

    getVmOptions(givenOptions, jreHome)
  }

  def getVmOptions(givenOptions: Seq[String], jreHome: Option[Path]): Seq[String] = {
    import DefaultOptions.*
    val ideaProxyOptions = proxyOptions { optName => !givenOptions.exists(_.startsWith(optName)) }

    val allOptions = ideaProxyOptions ++ givenOptions

    allOptions
      .addDefaultOption(ideaManaged.key, ideaManaged.value)
      .addDefaultOption(fileEncoding.key, fileEncoding.value)
      .addPermSize(jreHome)
  }


  /** @param select Allow only options that pass this filter on the option name */
  private def proxyOptions(select: String => Boolean): Seq[String] = {
    val optionsMap = SbtUtil.getStaticProxyConfigurationJvmOptions
    optionsMap.toSeq.collect { case (name, value) if select(name) => s"-D$name=$value" }
  }

  private implicit class OptionsOps(options: Seq[String]) {
    def addPermSize(jreHome: Option[Path]): Seq[String] = {
      import DefaultOptions.maxPermSize

      // use no MaxPermSize param if we know jdk version is >= 8 or user set it anyway
      val withoutPermSize = for {
        home <- jreHome
        if !hasOption(maxPermSize.key)
        jreVersion <- Option(JdkVersionDetector.getInstance().detectJdkVersionInfo(home.toCanonicalPath.toString))
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
  }
}
