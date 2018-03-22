package org.jetbrains.sbt
package project

import java.io.File
import java.net.URL
import java.util

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.externalSystem.model.{ExternalSystemException, ProjectSystemId}
import com.intellij.openapi.externalSystem.util._
import com.intellij.openapi.externalSystem.{ExternalSystemConfigurableAware, ExternalSystemManager}
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkType, JdkUtil, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Pair, SystemInfo}
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.Function
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.jps.model.java.JdkVersionDetector
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtExternalSystemConfigurable, SbtSystemSettings}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtProjectSettingsListener, SbtSystemSettings, SbtLocalSettings, SbtExecutionSettings]
    with ExternalSystemConfigurableAware
    with AutoImportAwareness {

  override def enhanceLocalProcessing(urls: util.List[URL]) {
    urls.add(jarWith[scala.App].toURI.toURL)
  }

  override def enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
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

  override def getSettingsProvider: Function[Project, SbtSystemSettings] = SbtSystemSettings.getInstance _

  override def getLocalSettingsProvider: Function[Project, SbtLocalSettings] = SbtLocalSettings.getInstance _

  override def getExecutionSettingsProvider: Function[Pair[Project, String], SbtExecutionSettings] =
    SbtExternalSystemManager.executionSettingsFor _

  override def getProjectResolverClass: Class[SbtProjectResolver] = classOf[SbtProjectResolver]

  override def getTaskManagerClass: Class[SbtTaskManager] = classOf[SbtTaskManager]

  override def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()

  override def getConfigurable(project: Project): Configurable = new SbtExternalSystemConfigurable(project)
}

object SbtExternalSystemManager {

  def executionSettingsFor(project: Project, path: String): SbtExecutionSettings = {
    val settings = SbtSystemSettings.getInstance(project)
    val projectSettings = Option(settings.getLinkedProjectSettings(path)).getOrElse(SbtProjectSettings.default)

    val customLauncher = settings.customLauncherEnabled.option(settings.getCustomLauncherPath).map(_.toFile)
    val customSbtStructureFile = settings.customSbtStructurePath.nonEmpty.option(settings.customSbtStructurePath.toFile)

    val realProjectPath = Option(projectSettings.getExternalProjectPath).getOrElse(path)
    val projectJdkName = bootstrapJdk(project, projectSettings)
    val vmExecutable = getVmExecutable(projectJdkName, settings)
    val jreHome = vmExecutable.parent.flatMap(_.parent)
    val vmOptions = getVmOptions(settings, jreHome)
    val environment = Map.empty ++ getAndroidEnvironmentVariables(projectJdkName)

    new SbtExecutionSettings(realProjectPath,
      vmExecutable, vmOptions, environment, customLauncher, customSbtStructureFile, projectJdkName,
      projectSettings.resolveClassifiers, projectSettings.resolveJavadocs, projectSettings.resolveSbtClassifiers,
      projectSettings.useSbtShell, projectSettings.enableDebugSbtShell)
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
    jdkInProject.orElse(jdkInImportSettings)
  }

  private def getVmExecutable(projectJdkName: Option[String], settings: SbtSystemSettings): File =
      if (!ApplicationManager.getApplication.isUnitTestMode)
        getRealVmExecutable(projectJdkName, settings)
      else
        getUnitTestVmExecutable

  private def getUnitTestVmExecutable: File = {
    val internalSdk = JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
    val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17 else internalSdk
    val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]
    new File(sdkType.getVMExecutablePath(sdk))
  }

  private def getRealVmExecutable(projectJdkName: Option[String], settings: SbtSystemSettings): File = {

    val customPath = settings.getCustomVMPath
    val customVmExecutable =
      if (settings.customVMEnabled && JdkUtil.checkForJre(customPath)) {
        val javaExe = if (SystemInfo.isWindows) "java.exe" else "java"
        Some(new File(customPath) / "bin" / javaExe)
      } else None

    val realExe = customVmExecutable.orElse {
      projectJdkName
        .flatMap(name => Option(ProjectJdkTable.getInstance().findJdk(name)))
        .map { sdk =>
          sdk.getSdkType match {
            case sdkType: JavaSdkType =>
              new File(sdkType.getVMExecutablePath(sdk))
            case _ =>
              // ugh
              throw new ExternalSystemException(SbtBundle("sbt.import.noProjectJvmFound"))
          }
        }
    }
    .orElse {
      val jdkType = JavaSdk.getInstance()
      Option(ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdkType))
        .map { sdk =>
          new File(jdkType.getVMExecutablePath(sdk))
        }
    }
    .getOrElse {
      throw new ExternalSystemException(SbtBundle("sbt.import.noCustomJvmFound"))
    }

    // workaround for https://youtrack.jetbrains.com/issue/IDEA-188247
    // TODO remove when fix is in platform (2018.2 at latest)
    if (realExe.isFile) realExe
    else {
      val parent = realExe.getParentFile
      val javaExe = if (SystemInfo.isWindows) "java.exe" else "java"
      parent / javaExe
    }
  }

  private def getAndroidEnvironmentVariables(projectJdkName: Option[String]): Map[String, String] =
    projectJdkName
      .flatMap(name => Option(ProjectJdkTable.getInstance().findJdk(name)))
      .flatMap { sdk =>
        try {
          sdk.getSdkType.isInstanceOf[AndroidSdkType].option(Map("ANDROID_HOME" -> sdk.getSdkModificator.getHomePath))
        } catch {
          case _ : NoClassDefFoundError => None
        }
      }.getOrElse(Map.empty)

  private def getVmOptions(settings: SbtSystemSettings, jreHome: Option[File]): Seq[String] = {
    import DefaultOptions._
    val userOptions = settings.getVmParameters.split("\\s+").toSeq.filter(_.nonEmpty)
    val ideaProxyOptions = proxyOptions { optName => !userOptions.exists(_.startsWith(optName)) }
    val allOptions = Seq(s"-Xmx${settings.getMaximumHeapSize}M") ++ ideaProxyOptions ++ userOptions

    allOptions
      .addDefaultOption(ideaManaged.key, ideaManaged.value)
      .addDefaultOption(fileEncoding.key, fileEncoding.value)
      .addPermSize(jreHome)
  }


  /** @param select Allow only options that pass this filter on option name */
  private def proxyOptions(select: String=>Boolean): Seq[String] = {

    val http = HttpConfigurable.getInstance
    val jvmArgs = http.getJvmProperties(false, null).asScala.map { pair => (pair.first, pair.second)}

    // TODO workaround for IDEA-186551 -- remove when fixed in core
    val nonProxyHosts =
      if (!StringUtil.isEmpty(http.PROXY_EXCEPTIONS)) {
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

  object DefaultOptions {
    case class JvmOption(key: String, value: String)

    val fileEncoding = JvmOption("-Dfile.encoding", "UTF-8")
    val maxPermSize = JvmOption("-XX:MaxPermSize", "256M")

    /** custom option to signal sbt instance is run from idea. */
    val ideaManaged = JvmOption("-Didea.managed", "true")
  }

}
