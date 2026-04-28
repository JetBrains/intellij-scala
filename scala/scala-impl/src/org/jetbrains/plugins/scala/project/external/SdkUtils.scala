package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.pom.java.LanguageLevel
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}

import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

object SdkUtils {

  private val Log = Logger.getInstance(getClass)

  def isJdkCompatibleWithEel(sdk: Sdk, eelDescriptor: EelDescriptor): Boolean = {
    val descriptor = for {
      home <- Option(sdk.getHomePath)
      path <- Try(Path.of(home)).toOption
    } yield EelProviderUtil.getEelDescriptor(path)
    val matches = descriptor.contains(eelDescriptor)
    if (!matches) {
      Log.debug(s"JDK '${sdk.getName}' (home=${sdk.getHomePath}) skipped: not compatible with $eelDescriptor")
    }
    matches
  }

  def findProjectSdk(eelDescriptor: EelDescriptor, sdkRef: SdkReference): Option[Sdk] =
    sdkRef match {
      case JdkByVersion(version) =>
        findMostRecentJdkConfiguredInIde(eelDescriptor)(sdk => Option(sdk.getVersionString).exists(_.contains(version)))
      case JdkByName(name) =>
        findMostRecentJdkConfiguredInIde(eelDescriptor)(_.getName == name)
          .orElse(findMostRecentJdkConfiguredInIde(eelDescriptor)(_.getName.contains(name)))
      case JdkByHome(homeFile) =>
        findMostRecentJdkConfiguredInIde(eelDescriptor) { sdk =>
          FileUtil.comparePaths(homeFile.toFile.getCanonicalPath, sdk.getHomePath) == 0 ||
            FileUtil.pathsEqual(homeFile.toAbsolutePath.toString, sdk.getHomePath)
        }
      case _ => None
    }

  @deprecated(message = "Use findProjectSdk(EelDescriptor, SdkReference)", since = "2026.1")
  @Deprecated(since = "2026.1", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def findProjectSdk(sdkRef: SdkReference): Option[Sdk] =
    findProjectSdk(LocalEelDescriptor.INSTANCE, sdkRef)

  def findMostRecentJdkConfiguredInIde(eelDescriptor: EelDescriptor)(condition: Sdk => Boolean): Option[Sdk] = {
    import scala.math.Ordering.comparatorToOrdering
    val sdkType = JavaSdk.getInstance()

    inReadAction {
      val jdks = ProjectJdkTable.getInstance()
        .getSdksOfType(JavaSdk.getInstance())
        .asScala
        .filter(sdk => isJdkCompatibleWithEel(sdk, eelDescriptor) && condition(sdk))

      if (jdks.isEmpty) None
      else Option(jdks.max(comparatorToOrdering(sdkType.versionComparator())))
    }
  }

  @deprecated(message = "Use findMostRecentJdkConfiguredInIde(EelDescriptor)(condition)", since = "2026.1")
  @Deprecated(since = "2026.1", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
  def findMostRecentJdkConfiguredInIde(condition: Sdk => Boolean): Option[Sdk] =
    findMostRecentJdkConfiguredInIde(LocalEelDescriptor.INSTANCE)(condition)

  def mostRecentRegisteredJdk(eelDescriptor: EelDescriptor): Option[Sdk] =
    findMostRecentJdkConfiguredInIde(eelDescriptor)(_ => true)

  // Not deprecated: still used by out-of-scope callers (BSP). Once those are migrated to the
  // EEL-aware overload, this can be deprecated together with #findOrCreateSdk(SdkReference).
  def mostRecentRegisteredJdk: Option[Sdk] =
    mostRecentRegisteredJdk(LocalEelDescriptor.INSTANCE)

  def defaultJavaLanguageLevelIn(jdk: Sdk): Option[LanguageLevel] =
    Option(LanguageLevel.parse(jdk.getVersionString))

  private def resolveName(home: Path): String = {
    val fileName = home.getFileName
    val suffix = if (fileName.toString == "jre") home.getParent.getFileName else fileName
    val baseName = s"BSP_$suffix"
    val names = ProjectJdkTable.getInstance.getAllJdks.map(_.getName)
    if (names.contains(baseName)) {
      baseName + DigestUtils.md5Hex(home.toString).take(10)
    } else {
      baseName
    }
  }

  def findOrCreateSdk(eelDescriptor: EelDescriptor, sdkReference: SdkReference): Option[Sdk] = {
    def createFromHome = {
      Option(sdkReference).collect {
        case JdkByHome(home) =>
          val name = resolveName(home)
          val newJdk = JavaSdk.getInstance.createJdk(name, home.toString, home.getFileName.toString == "jre")
          ProjectJdkTable.getInstance.addJdk(newJdk)
          newJdk
      }
    }

    findProjectSdk(eelDescriptor, sdkReference).orElse(createFromHome)
  }

  // Not deprecated: still used by out-of-scope callers (BSP). Once those are migrated to the
  // EEL-aware overload, this can be deprecated together with #mostRecentRegisteredJdk.
  def findOrCreateSdk(sdkReference: SdkReference): Option[Sdk] =
    findOrCreateSdk(LocalEelDescriptor.INSTANCE, sdkReference)

  def addJdkIfNotExists(sdk: Sdk): Unit = {
    val projectJdkTable = ProjectJdkTable.getInstance()
    if (projectJdkTable.findJdk(sdk.getName) == null)
      inWriteAction {
        projectJdkTable.addJdk(sdk)
      }
  }
}
