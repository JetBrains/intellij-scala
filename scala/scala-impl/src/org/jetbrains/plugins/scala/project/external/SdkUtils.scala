package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelMachineProviderUtil, EelNioBridgeServiceKt, EelProviderUtil, LocalEelDescriptor, LocalEelMachine}
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.EelUtilsKt
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}

import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

object SdkUtils {

  @deprecated(message = "Use findProjectSdk(SdkReference, EelDescriptor)", since = "2026.2")
  @Deprecated(since = "2026.2", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  def findProjectSdk(sdkRef: SdkReference): Option[Sdk] =
    findProjectSdk(sdkRef, LocalEelDescriptor.INSTANCE)

  def findProjectSdk(sdkRef: SdkReference, project: Project): Option[Sdk] =
    findProjectSdk(sdkRef, EelProviderUtil.getEelDescriptor(project))

  def findProjectSdk(sdkRef: SdkReference, eelDescriptor: EelDescriptor): Option[Sdk] =
    sdkRef match {
      case JdkByVersion(version) => findMostRecentJdkConfiguredInIde(sdk => Option(sdk.getVersionString).exists(_.contains(version)), eelDescriptor)
      case JdkByName(version)    => findMostRecentJdkConfiguredInIde(_.getName == version, eelDescriptor).orElse(findMostRecentJdkConfiguredInIde(_.getName.contains(version), eelDescriptor))
      case JdkByHome(homeFile)   => findMostRecentJdkConfiguredInIde(
        sdk =>
          if (eelDescriptor == LocalEelDescriptor.INSTANCE) {
            // Based on the commit history, this sophisticated comparison was added to cover some
            // edge case (although not much detail is available), so let's keep it as it is for safety.
            val canonicalHomePath = Try(homeFile.toRealPath()).getOrElse(homeFile.toAbsolutePath.normalize()).toString
            FileUtil.comparePaths(canonicalHomePath, sdk.getHomePath) == 0 ||
              FileUtil.pathsEqual(homeFile.toAbsolutePath.toString, sdk.getHomePath)
          } else {
            val sdkPath = EelNioBridgeServiceKt.asEelPath(Path.of(sdk.getHomePath), eelDescriptor)
            val homeFilePath = EelNioBridgeServiceKt.asEelPath(homeFile, eelDescriptor)
            sdkPath == homeFilePath
          },
        eelDescriptor
      )
      case _                     => None
    }

  @deprecated(message = "Use findMostRecentJdkConfiguredInIde(Sdk => Boolean, EelDescriptor)", since = "2026.2")
  @Deprecated(since = "2026.2", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  def findMostRecentJdkConfiguredInIde(condition: Sdk => Boolean): Option[Sdk] =
    findMostRecentJdkConfiguredInIde(condition, LocalEelDescriptor.INSTANCE)

  def findMostRecentJdkConfiguredInIde(condition: Sdk => Boolean, eelDescriptor: EelDescriptor): Option[Sdk] = {
    import scala.math.Ordering.comparatorToOrdering
    val sdkType = JavaSdk.getInstance()
    val eelMachine = Option(EelMachineProviderUtil.getResolvedEelMachine(eelDescriptor)).getOrElse(LocalEelMachine.INSTANCE)

    inReadAction {
      val jdks = ProjectJdkTable.getInstance()
        .getSdksOfType(JavaSdk.getInstance())
        .asScala
        .filter(sdk => EelUtilsKt.ownsSdk(eelMachine, sdk) && condition(sdk))

      if (jdks.isEmpty) None
      else Option(jdks.max(comparatorToOrdering(sdkType.versionComparator())))
    }
  }

  @deprecated(message = "Use mostRecentRegisteredJdk(EelDescriptor)", since = "2026.2")
  @Deprecated(since = "2026.2", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  def mostRecentRegisteredJdk: Option[Sdk] =
    findMostRecentJdkConfiguredInIde(_ => true)

  def mostRecentRegisteredJdk(eelDescriptor: EelDescriptor): Option[Sdk] =
    findMostRecentJdkConfiguredInIde(_ => true, eelDescriptor)

  def mostRecentRegisteredJdk(project: Project): Option[Sdk] =
    findMostRecentJdkConfiguredInIde(_ => true, EelProviderUtil.getEelDescriptor(project))

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

  @deprecated(message = "Use findOrCreateSdk(SdkReference, Project)", since = "2026.2")
  @Deprecated(since = "2026.2", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  def findOrCreateSdk(sdkReference: SdkReference): Option[Sdk] = {
    def createFromHome = {
      Option(sdkReference).collect {
        case JdkByHome(home) =>
          val name = resolveName(home)
          val newJdk = JavaSdk.getInstance.createJdk(name, home.toString, home.getFileName.toString == "jre")
          ProjectJdkTable.getInstance.addJdk(newJdk)
          newJdk
      }
    }

    SdkUtils.findProjectSdk(sdkReference).orElse(createFromHome)
  }

  def findOrCreateSdk(sdkReference: SdkReference, project: Project): Option[Sdk] = {
    def createFromHome =
      Option(sdkReference).collect {
        case JdkByHome(home) =>
          val name = resolveName(home)
          val newJdk = JavaSdk.getInstance.createJdk(name, home.toString, home.getFileName.toString == "jre")
          ProjectJdkTable.getInstance(project).addJdk(newJdk)
          newJdk
      }

    SdkUtils.findProjectSdk(sdkReference, project).orElse(createFromHome)
  }

  def addJdkIfNotExists(sdk: Sdk): Unit = {
    val projectJdkTable = ProjectJdkTable.getInstance()
    if (projectJdkTable.findJdk(sdk.getName) == null)
      inWriteAction {
        projectJdkTable.addJdk(sdk)
      }
  }
}
