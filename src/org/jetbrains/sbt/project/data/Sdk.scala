package org.jetbrains.sbt.project.data

import java.io.File

import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.projectRoots
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.sdk.{AndroidPlatform, AndroidSdkType}

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 7/14/15.
 */
sealed trait Sdk

final case class JdkByVersion(version: String) extends Sdk
final case class JdkByHome(home: File) extends Sdk
final case class Android(version: String) extends Sdk

object SdkUtils {
  def findProjectSdk(sdk: Sdk): Option[projectRoots.Sdk] = sdk match {
    case Android(version) => findAndroidJdkByVersion(version)
    case JdkByVersion(version) => allJdks.find(_.getName.contains(version))
    case JdkByHome(homeFile) => findJdkByHome(homeFile)
  }

  def allAndroidSdks: Seq[projectRoots.Sdk] =
    ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()).asScala

  def allJdks: Seq[projectRoots.Sdk] = ProjectJdkTable.getInstance.getSdksOfType(JavaSdk.getInstance).asScala

  def defaultJavaLanguageLevelIn(jdk: projectRoots.Sdk): Option[LanguageLevel] = {
    val JavaLanguageLevels = Map(
      "1.3" -> LanguageLevel.JDK_1_3,
      "1.4" -> LanguageLevel.JDK_1_4,
      "1.5" -> LanguageLevel.JDK_1_5,
      "1.6" -> LanguageLevel.JDK_1_6,
      "1.7" -> LanguageLevel.JDK_1_7,
      "1.8" -> LanguageLevel.JDK_1_8,
      "1.9" -> LanguageLevel.JDK_1_9)
    val jdkVersion = jdk.getVersionString

    JavaLanguageLevels.collectFirst {
      case (name, level) if jdkVersion.contains(name) => level
    }
  }

  def javaLanguageLevelFrom(javacOptions: Seq[String]): Option[LanguageLevel] = {
    for {
      sourcePos <- Option(javacOptions.indexOf("-source")).filterNot(_ == -1)
      sourceValue <- javacOptions.lift(sourcePos + 1)
      languageLevel <- Option(LanguageLevel.parse(sourceValue))
    } yield languageLevel
  }

  private def findAndroidJdkByVersion(version: String): Option[projectRoots.Sdk] = {
    def isGEQAsInt(fst: String, snd: String): Boolean =
      try {
        val fstInt = fst.toInt
        val sndInt = snd.toInt
        fstInt >= sndInt
      } catch {
        case exc: NumberFormatException => false
      }

    val matchingSdks = for {
      sdk <- allAndroidSdks
      platformVersion <- Option(AndroidPlatform.getInstance(sdk)).map(_.getApiLevel.toString)
      if (isGEQAsInt(platformVersion, version))
    } yield sdk
    matchingSdks.headOption
  }

  private def findJdkByHome(homeFile: File): Option[projectRoots.Sdk] = {
    allJdks.find(jdk => FileUtil.comparePaths(homeFile.getCanonicalPath, jdk.getHomePath) == 0)
  }
}
