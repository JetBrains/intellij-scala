package org.jetbrains.sbt
package project

import java.io.File
import java.net.URL
import java.util

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.openapi.application.{ApplicationManager, PathManager}
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.util._
import com.intellij.openapi.externalSystem.{ExternalSystemAutoImportAware, ExternalSystemConfigurableAware, ExternalSystemManager}
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.projectRoots.{JavaSdkType, ProjectJdkTable}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.sbt.project.settings._
import org.jetbrains.sbt.settings.{SbtExternalSystemConfigurable, SbtSystemSettings}

import scala.collection.mutable

/**
 * @author Pavel Fatin
 */
class SbtExternalSystemManager
  extends ExternalSystemManager[SbtProjectSettings, SbtProjectSettingsListener, SbtSystemSettings, SbtLocalSettings, SbtExecutionSettings]
  with ExternalSystemConfigurableAware {

  def enhanceLocalProcessing(urls: util.List[URL]) {
    urls.add(jarWith[scala.App].toURI.toURL)
  }

  def enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
    val classpath = parameters.getClassPath

    classpath.add(jarWith[this.type])
    classpath.add(jarWith[scala.App])
    classpath.add(jarWith[scala.xml.Node])

//    val vmParameters = parameters.getVMParametersList
//    vmParameters.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")

    parameters.getVMParametersList.addProperty(
      ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, SbtProjectSystem.Id.getId)

    parameters.getVMParametersList.addProperty(
      PathManager.PROPERTY_LOG_PATH, PathManager.getLogPath)
  }

  def getSystemId = SbtProjectSystem.Id

  def getSettingsProvider = SbtSystemSettings.getInstance _

  def getLocalSettingsProvider = SbtLocalSettings.getInstance _

  def getExecutionSettingsProvider = SbtExternalSystemManager.executionSettingsFor _

  def getProjectResolverClass = classOf[SbtProjectResolver]

  def getTaskManagerClass = classOf[SbtTaskManager]

  def getExternalProjectDescriptor = new SbtOpenProjectDescriptor()

  def getConfigurable(project: Project): Configurable = new SbtExternalSystemConfigurable(project)
}

object SbtExternalSystemManager {
  def executionSettingsFor(project: Project, path: String) = {

    val settings = SbtSystemSettings.getInstance(project)

    val customLauncher = settings.customLauncherEnabled
      .option(settings.getCustomLauncherPath).map(_.toFile)

    val customSbtStructureDir = settings.getCustomSbtStructureDir match {
      case "" => None
      case str => Some(str)
    }

    val vmOptions = {
      val userOptions = settings.getVmParameters.split("\\s+").toSeq
      val ideaProxyOptions = proxyOptionsFor(HttpConfigurable.getInstance).filterNot { opt =>
        val optName = opt.split('=').head + "="
        userOptions.exists(_.startsWith(optName))
      }
      Seq(s"-Xmx${settings.getMaximumHeapSize}M") ++ userOptions ++ ideaProxyOptions
    }

    val customVmFile = new File(settings.getCustomVMPath) / "bin" / "java"
    val customVmExecutable = settings.customVMEnabled.option(customVmFile)

    val projectSettings = Option(settings.getLinkedProjectSettings(path)).getOrElse(SbtProjectSettings.default)

    val projectJdkName = Option(ProjectRootManager.getInstance(project).getProjectSdk)
                            .map(_.getName)
                            .orElse(projectSettings.jdkName)

    val environment = mutable.Map.empty[String, String]

    val vmExecutable = if (!ApplicationManager.getApplication.isUnitTestMode) {
      customVmExecutable.orElse {
        val projectSdk = projectJdkName.flatMap(name => Option(ProjectJdkTable.getInstance().findJdk(name)))

        projectSdk.map { sdk =>
          sdk.getSdkType match {
            case sdkType : JavaSdkType =>
              try {
                if (sdkType.isInstanceOf[AndroidSdkType])
                  environment += ("ANDROID_HOME" -> sdk.getSdkModificator.getHomePath)
              } catch {
                case _ : NoClassDefFoundError => // no android plugin, do nothing
              }
              new File(sdkType.getVMExecutablePath(sdk))
            case _ => throw new ExternalSystemException(SbtBundle("sbt.import.noProjectJvmFound"))
          }
        }
      } getOrElse {
        throw new ExternalSystemException(SbtBundle("sbt.import.noCustomJvmFound"))
      }
    } else {
      val internalSdk =
        JavaAwareProjectJdkTableImpl.getInstanceEx.getInternalJdk
      val sdk = if (internalSdk == null) IdeaTestUtil.getMockJdk17
      else internalSdk
      val sdkType = sdk.getSdkType.asInstanceOf[JavaSdkType]
      new File(sdkType.getVMExecutablePath(sdk))
    }

    new SbtExecutionSettings(vmExecutable, vmOptions, environment.toMap, customLauncher, customSbtStructureDir, projectJdkName,
      projectSettings.resolveClassifiers, projectSettings.resolveSbtClassifiers)
  }

  private def proxyOptionsFor(http: HttpConfigurable): Seq[String] = {
    val useProxy = http.USE_HTTP_PROXY && !http.PROXY_TYPE_IS_SOCKS
    val useCredentials = useProxy && http.PROXY_AUTHENTICATION

    useProxy.seq(s"-Dhttp.proxyHost=${http.PROXY_HOST}", s"-Dhttp.proxyPort=${http.PROXY_PORT}") ++
      useCredentials.seq(s"-Dhttp.proxyUser=${http.PROXY_LOGIN}", s"-Dhttp.proxyPassword=${http.getPlainProxyPassword}")
  }
}
