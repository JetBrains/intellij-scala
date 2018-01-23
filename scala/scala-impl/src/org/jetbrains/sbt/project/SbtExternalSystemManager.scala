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
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkType, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.Function
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtExternalSystemConfigurable, SbtSystemSettings}

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtProjectSettingsListener, SbtSystemSettings, SbtLocalSettings, SbtExecutionSettings]
  with ExternalSystemConfigurableAware {

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
    val vmOptions = getVmOptions(settings)
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
    val customVmFile = new File(settings.getCustomVMPath) / "bin" / "java"
    val customVmExecutable = settings.customVMEnabled.option(customVmFile)
    val jdkType = JavaSdk.getInstance()

    customVmExecutable.orElse {
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
      Option(ProjectJdkTable.getInstance().findMostRecentSdkOfType(jdkType))
        .map { sdk =>
          new File(jdkType.getVMExecutablePath(sdk))
        }
    }
    .getOrElse {
      throw new ExternalSystemException(SbtBundle("sbt.import.noCustomJvmFound"))
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

  private def getVmOptions(settings: SbtSystemSettings): Seq[String] = {
    val userOptions = settings.getVmParameters.split("\\s+").toSeq.filter(_.nonEmpty)
    val ideaProxyOptions = proxyOptionsFor(HttpConfigurable.getInstance).filterNot { opt =>
      val optName = opt.split('=').head + "="
      userOptions.exists(_.startsWith(optName))
    }
    Seq(s"-Xmx${settings.getMaximumHeapSize}M") ++ userOptions ++ ideaProxyOptions ++ fileEncoding(userOptions)
  }

  private def fileEncoding(userOptions: Seq[String]): Option[String] = {
    val prefix = "-Dfile.encoding"
    if (userOptions.exists(_.startsWith(s"$prefix="))) None
    else Some(s"$prefix=UTF-8")
  }

  // TODO We should probably rely on the more complete HttpConfigurable.getInstance.getJvmProperties()
  private def proxyOptionsFor(http: HttpConfigurable): Seq[String] = {
    val useProxy = http.USE_HTTP_PROXY && !http.PROXY_TYPE_IS_SOCKS
    val useCredentials = useProxy && http.PROXY_AUTHENTICATION

    useProxy.seq(s"-Dhttp.proxyHost=${http.PROXY_HOST}", s"-Dhttp.proxyPort=${http.PROXY_PORT}") ++
      useCredentials.seq(s"-Dhttp.proxyUser=${http.getProxyLogin}", s"-Dhttp.proxyPassword=${http.getPlainProxyPassword}")
  }
}
