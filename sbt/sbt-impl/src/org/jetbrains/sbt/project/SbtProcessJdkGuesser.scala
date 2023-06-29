package org.jetbrains.sbt.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.projectRoots.impl.JavaHomeFinder
import com.intellij.openapi.projectRoots.{JavaSdk, JdkUtil, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.SbtBundle

import java.util.Comparator
import scala.jdk.CollectionConverters._
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

  private val MINIMUM_JAVA_VERSION_STR: String = "1.8"
  private val MINIMUM_JAVA_VERSION: JavaVersion = JavaVersion.parse(MINIMUM_JAVA_VERSION_STR)

  private val MAXIMUM_JAVA_VERSION_STR: String = "17"
  private val MAXIMUM_JAVA_VERSION: JavaVersion = JavaVersion.parse(MAXIMUM_JAVA_VERSION_STR)

  private val jdkType: JavaSdk = JavaSdk.getInstance
  private val versionComparator: Comparator[Sdk] = jdkType.versionComparator
  private val versionStringComparator: Comparator[String] = jdkType.versionStringComparator
  private val versionOrdering: Ordering[Sdk] = Ordering.comparatorToOrdering(versionComparator)

  /**
   * This is an alternative to [[com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl#preconfigure()]] which selects most recent JDK<br>
   * To be safe we don't need most recent JDK (see docs for [[findJdkWithSuitableVersion]])
   */
  @RequiresEdt
  def preconfigureJdkForSbt(jdkTable: ProjectJdkTable): Unit = {
    try {
      val jdkOpt = ProgressManager.getInstance.runProcessWithProgressSynchronously(
        (() => createJdkWithSuitableVersion): ThrowableComputable[Option[Sdk], Exception],
        SbtBundle.message("sbt.import.detecting.jdk"),
        true,
        null
      )
      jdkOpt.foreach { jdk =>
        inWriteAction {
          val existingJdk = Option(jdkTable.findJdk(jdk.getName))
            .filter(sdk => sdk.getHomePath != null &&  jdk.getHomePath == sdk.getHomePath)
          if (existingJdk.isEmpty) jdkTable.addJdk(jdk)
        }
      }
    } catch {
      case _: ProcessCanceledException =>
      //ignore
    }
  }

  case class SdkCandidate(sdk: Option[Sdk], allSdkSorted: Seq[Sdk])

  def findJdkWithSuitableVersion(jdkTable: ProjectJdkTable): SdkCandidate = {
    val sdksAll = jdkTable.getSdksOfType(jdkType).asScala.toSeq
    val sdksAllSorted = sdksAll.sorted(versionOrdering)
    val sdksMatchingVersion = sdksAllSorted.filter { sdk =>
      val versionString = sdk.getVersionString
      val versionPath = JavaVersion.tryParse(sdk.getHomePath)
      (versionStringComparator.compare(versionString, MINIMUM_JAVA_VERSION_STR) >= 0 &&
        versionStringComparator.compare(versionString, MAXIMUM_JAVA_VERSION_STR) <= 0) ||
        (versionPath != null && MINIMUM_JAVA_VERSION <= versionPath && versionPath <= MAXIMUM_JAVA_VERSION)
    }

    if (Log.isTraceEnabled) {
      Log.trace(s"findMostSuitableJdkForSbt: all sdks:\n${sdksAllSorted.mkString("\n")}")
    }

    SdkCandidate(sdksMatchingVersion.headOption, sdksAllSorted)
  }

  /** Alternative for [[com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl#guessJdk()]] */
  private def createJdkWithSuitableVersion: Option[Sdk] = {
    val javaPaths0 = JavaHomeFinder.suggestHomePaths(false).asScala.toSeq

    val javaPaths: Seq[JavaPathWithVersion] =
      javaPaths0
        .filter(jdkType.isValidSdkHome)
        .map(p => JavaPathWithVersion(p, JavaVersion.tryParse(p)))
        .filter(_.version != null)

    val javaPathsMatchingVersion: Seq[JavaPathWithVersion] =
      javaPaths.filter(s => MINIMUM_JAVA_VERSION <= s.version && s.version <= MAXIMUM_JAVA_VERSION)

    val homePath = javaPathsMatchingVersion.headOption match {
      case Some(value) => value
      case None =>
        return None
    }
    val suggestedName = JdkUtil.suggestJdkName(homePath.version, null)
    if (suggestedName == null)
      return None

    ProgressManager.checkCanceled()

    Option(jdkType.createJdk(suggestedName, homePath.path, false))
  }

  private case class JavaPathWithVersion(path: String, version: JavaVersion)
}
