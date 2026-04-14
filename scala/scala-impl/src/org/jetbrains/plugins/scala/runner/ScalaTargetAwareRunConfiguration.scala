package org.jetbrains.plugins.scala.runner

import com.intellij.execution.CantRunException
import com.intellij.execution.configurations.{ConfigurationFactory, JavaParameters, ModuleBasedConfiguration, RunConfigurationModule}
import com.intellij.execution.target.java.{JavaLanguageRuntimeConfiguration, JavaLanguageRuntimeType}
import com.intellij.execution.target.{LanguageRuntimeType, TargetEnvironmentAwareRunProfile, TargetEnvironmentConfiguration}
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.annotations.ApiStatus

import java.nio.file.Path

/**
 * Base class for Scala run configurations that support remote execution targets (eel/WSL).
 *
 * Implements the [[TargetEnvironmentAwareRunProfile]] interface following the same pattern
 * as `com.intellij.execution.junit.JUnitConfiguration`.
 */
@ApiStatus.Internal
abstract class ScalaTargetAwareRunConfiguration[M <: RunConfigurationModule, S](
  name: String,
  module: M,
  factory: ConfigurationFactory
) extends ModuleBasedConfiguration[M, S](name, module, factory)
  with TargetEnvironmentAwareRunProfile {

  /**
   * An alternative JRE path to check when determining whether the run configuration
   * runs under a remote JDK. Subclasses that support alternative JRE settings
   * should override this to return the configured alternative JRE path.
   */
  protected def alternativeJrePath: Option[String] = None

  override def canRunOn(target: TargetEnvironmentConfiguration): Boolean =
    target.getRuntimes.findByType(classOf[JavaLanguageRuntimeConfiguration]) != null

  override def getDefaultLanguageRuntimeType: LanguageRuntimeType[?] =
    LanguageRuntimeType.EXTENSION_NAME.findExtension(classOf[JavaLanguageRuntimeType])

  override def getDefaultTargetName: String = getOptions.getRemoteTarget

  override def setDefaultTargetName(targetName: String): Unit = {
    getOptions.setRemoteTarget(targetName)
  }

  override def needPrepareTarget(): Boolean =
    super.needPrepareTarget() || runsUnderRemoteJdk()

  /**
   * Same as `com.intellij.execution.JavaRunConfigurationBase#runsUnderRemoteJdk`.
   */
  private def runsUnderRemoteJdk(): Boolean = {
    //noinspection ApiStatus,UnstableApiUsage
    val pathNotLocal: Path => Boolean = !EelPathUtils.isPathLocal(_)
    val stringToPath: String => Path = Path.of(_)
    jdkHomeSatisfies(stringToPath andThen pathNotLocal)
  }

  /**
   * Same as `com.intellij.execution.JavaRunConfigurationBase#jdkHomeSatisfies`.
   */
  private def jdkHomeSatisfies(predicate: String => Boolean): Boolean = {
    alternativeJrePath match {
      case Some(path) =>
        val sdk = ProjectJdkTable.getInstance().findJdk(path)
        if (sdk != null) {
          val homePath = sdk.getHomePath
          if (homePath != null) return predicate(homePath)
        }
        return predicate(path)
      case None =>
    }

    val module = getConfigurationModule.getModule
    if (module != null) {
      val sdk = try {
        JavaParameters.getValidJdkToRunModule(module, /* productionOnly = */ false)
      } catch {
        case _: CantRunException => return false
      }
      val sdkHomePath = sdk.getHomePath
      return sdkHomePath != null && predicate(sdkHomePath)
    }

    false
  }
}
