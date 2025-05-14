package org.jetbrains.sbt.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.projectRoots.impl.{JavaHomeFinder, SdkConfigurationUtil}
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.annotations.{RequiresBackgroundThread, RequiresEdt}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.template.wizard.JdkSbtCompatibilityChecker
import org.jetbrains.sbt.{SbtBundle, SbtVersion}

import java.util
import java.util.Comparator
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.math.Ordered.orderingToOrdered

/**
 * This class contains helper utilities to find JDK with most suitable version to run SBT.
 * It's only needed when Project SDK is not configured, for example during initial project loading.
 * Using "latest" java is not an option, because if a user has some non-release JDK version installed then SBT import process can fail.
 * (e.g. sbt 1.5.1 doesn't support JDK 18)
 *
 * @note In some cases it won't work,
 *       e.g. if user uses old SBT version which only supports JDK 8 but user has JDK 17 installed
 *
 * @see https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html
 */
object SbtProcessJdkGuesser {

  private val Log = Logger.getInstance(getClass)

  private val MINIMUM_JAVA_SDK_VERSION: JavaSdkVersion = JavaSdkVersion.JDK_1_8
  private val MAXIMUM_JAVA_SDK_VERSION: JavaSdkVersion = JavaSdkVersion.JDK_17

  private val jdkType: JavaSdk = JavaSdk.getInstance
  private val versionComparator: Comparator[Sdk] = jdkType.versionComparator
  private val versionOrdering: Ordering[Sdk] = Ordering.comparatorToOrdering(versionComparator)

  /**
   * This is an alternative to [[com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl#preconfigure()]] which selects most recent JDK<br>
   * To be safe we don't need most recent JDK (see docs for [[findJdkWithSuitableVersion]])
   */
  @RequiresEdt
  def preconfigureJdkForSbt(jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): Unit = {
    try {
      val jdkOpt = ProgressManager.getInstance.runProcessWithProgressSynchronously(
        (() => createJdkWithSuitableVersion(sbtVersion)): ThrowableComputable[Option[Sdk], Exception],
        SbtBundle.message("sbt.import.detecting.jdk"),
        true,
        null
      )
      jdkOpt.foreach { jdk =>
        inWriteAction {
          jdkTable.addJdk(jdk)
        }
      }
    } catch {
      case _: ProcessCanceledException =>
      //ignore
    }
  }

  case class SdkCandidate(sdk: Option[Sdk], allSdkSorted: Seq[Sdk])

  def findJdkWithSuitableVersion(jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): SdkCandidate = {
    val sdksAll = jdkTable.getSdksOfType(jdkType).asScala.toSeq
    val sdksAllSorted = sdksAll.sorted(versionOrdering)
    val filteredBySbt = sdksAllSorted.filter { sdk =>
      isSbtJdkCompatible(jdkType.getVersion(sdk), sbtVersion)
    }

    val sdksMatchingVersion =
      if (filteredBySbt.nonEmpty) filteredBySbt
      else {
        sdksAllSorted.filter { sdk =>
          isInRange(jdkType.getVersion(sdk))
        }
      }

    if (Log.isTraceEnabled) {
      Log.trace(s"findMostSuitableJdkForSbt: all sdks:\n${sdksAllSorted.mkString("\n")}")
    }

    SdkCandidate(sdksMatchingVersion.headOption, sdksAllSorted)
  }

  /**
   * Tries to find all existing sdk home paths on the local machine
   *
   * @return suggested sdk home paths and corresponding versions
   */
  @RequiresBackgroundThread
  def findAllExistingJavaPaths(jdkType: JavaSdk): Seq[(String, JavaVersion)] = {
    val javaPaths = JavaHomeFinder.suggestHomePaths(false).asScala.toSeq
    javaPaths
      .filter(jdkType.isValidSdkHome)
      .flatMap { path => Option(JavaVersion.tryParse(path)).map(path -> _) }
  }

  private def isSbtJdkCompatible(@Nullable sdk: JavaSdkVersion, sbtVersion: SbtVersion): Boolean = {
    sdk != null && JdkSbtCompatibilityChecker.isSbtAndJdkVersionCompatible(sdk.getMaxLanguageLevel.toJavaVersion, sbtVersion, strict = true)
  }

  private def isInRange(@Nullable sdk: JavaSdkVersion): Boolean =
    sdk != null && MINIMUM_JAVA_SDK_VERSION <= sdk && sdk <= MAXIMUM_JAVA_SDK_VERSION


  /** Alternative for [[com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl#guessJdk()]] */
  @RequiresBackgroundThread
  private def createJdkWithSuitableVersion(sbtVersion: SbtVersion): Option[Sdk] = {
    val javaPaths0 = findAllExistingJavaPaths(jdkType)

    val javaPaths = javaPaths0.flatMap { case (path, version) =>
      Option(JavaSdkVersion.fromJavaVersion(version)).map(JavaPathWithVersion(path, version, _))
    }

    val filteredBySbt = javaPaths.filter(javaPath => isSbtJdkCompatible(javaPath.sdkVersion, sbtVersion))

    val javaPathsMatchingVersion =
      if (filteredBySbt.nonEmpty) filteredBySbt
      else {
        javaPaths.filter(javaPath => isInRange(javaPath.sdkVersion))
      }


    val homePath = javaPathsMatchingVersion.headOption match {
      case Some(value) => value
      case None =>
        return None
    }
    val allJdks = ProjectJdkTable.getInstance().getAllJdks
    val suggestedName = SdkConfigurationUtil.createUniqueSdkName(JavaSdk.getInstance(), homePath.path, util.Arrays.asList(allJdks:_*))

    ProgressManager.checkCanceled()

    Option(jdkType.createJdk(suggestedName, homePath.path, false))
  }

  private case class JavaPathWithVersion(path: String, version: JavaVersion, sdkVersion: JavaSdkVersion)
}
