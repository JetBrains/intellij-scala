package org.jetbrains.sbt.project.data

import java.io.File

import com.intellij.openapi.projectRoots
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk => OaSdk}
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.sdk.{AndroidPlatform, AndroidSdkType}
import org.jetbrains.plugins.scala.extensions.inReadAction

import scala.collection.JavaConverters._

/**
 * @author Nikolay Obedin
 * @since 7/14/15.
 */
sealed abstract class Sdk

final case class JdkByName(name: String) extends Sdk
final case class JdkByHome(home: File) extends Sdk
final case class JdkByVersion(version: String) extends Sdk
final case class Android(version: String) extends Sdk

object SdkUtils {
  def findProjectSdk(sdk: Sdk): Option[projectRoots.Sdk] = {

    sdk match {
      case Android(version) =>
        findAndroidJdkByVersion(version)
      case JdkByVersion(version) =>
        findMostRecentJdk { sdk: projectRoots.Sdk =>
          Option(sdk.getVersionString).exists(s => s.contains(version))
        }
      case JdkByName(version) =>
        findMostRecentJdk { sdk: projectRoots.Sdk => sdk.getName.contains(version) }
      case JdkByHome(homeFile) =>
        findMostRecentJdk {sdk: projectRoots.Sdk =>
          FileUtil.comparePaths(homeFile.getCanonicalPath, sdk.getHomePath) == 0
        }
    }
  }

  def findMostRecentJdk(condition: projectRoots.Sdk => Boolean): Option[projectRoots.Sdk] = {
    val jdkCondition = { sdk: projectRoots.Sdk => sdk.getSdkType == JavaSdk.getInstance }
    val combinedCondition = { sdk: projectRoots.Sdk => sdk != null && condition(sdk) && jdkCondition(sdk) }
    Option(inReadAction(ProjectJdkTable.getInstance().findMostRecentSdk(combinedCondition)))
  }

  def mostRecentJdk: Option[projectRoots.Sdk] =
    findMostRecentJdk(_ => true)

  def allAndroidSdks: Seq[projectRoots.Sdk] =
    inReadAction(ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()).asScala)


  def defaultJavaLanguageLevelIn(jdk: projectRoots.Sdk): Option[LanguageLevel] = {
    val JavaLanguageLevels = Map(
      "1.3" -> LanguageLevel.JDK_1_3,
      "1.4" -> LanguageLevel.JDK_1_4,
      "1.5" -> LanguageLevel.JDK_1_5,
      "1.6" -> LanguageLevel.JDK_1_6,
      "1.7" -> LanguageLevel.JDK_1_7,
      "1.8" -> LanguageLevel.JDK_1_8,
      "1.9" -> LanguageLevel.JDK_1_9)
    val jdkVersion = Option(jdk.getVersionString).getOrElse(jdk.getName)

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
        case _: NumberFormatException => false
      }

    val matchingSdks = for {
      sdk <- allAndroidSdks
      platform <- Option(AndroidPlatform.getInstance(sdk))
      platformVersion = platform.getApiLevel.toString
      if isGEQAsInt(platformVersion, version)
    } yield sdk

    matchingSdks.headOption
  }

  private implicit def asCondition[A](f: A => Boolean): Condition[A] = new Condition[A] {
    override def value(a: A): Boolean = f(a)
  }
}
