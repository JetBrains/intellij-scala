package org.jetbrains.sbt.project.data

import java.io.File

import com.intellij.openapi.projectRoots.{Sdk, JavaSdk, ProjectJdkTable}
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.extensions.inReadAction


/**
 * @author Nikolay Obedin
 * @since 7/14/15.
 */
sealed abstract class SdkReference

// TODO Refactor
final case class JdkByName(name: String) extends SdkReference
final case class JdkByHome(home: File) extends SdkReference
final case class JdkByVersion(version: String) extends SdkReference
final case class AndroidJdk(version: String) extends SdkReference

object SdkUtils {
  def findProjectSdk(sdk: SdkReference): Option[Sdk] = {
    SdkResolver.EP_NAME.getExtensions
      .view
      .flatMap(_.sdkOf(sdk))
      .headOption
      .orElse {
        sdk match {
          case JdkByVersion(version) => findMostRecentJdk(sdk => Option(sdk.getVersionString).exists(_.contains(version)))
          case JdkByName(version) => findMostRecentJdk(_.getName.contains(version))
          case JdkByHome(homeFile) => findMostRecentJdk(sdk => FileUtil.comparePaths(homeFile.getCanonicalPath, sdk.getHomePath) == 0)
          case _ => None
        }
      }
  }

  private def findMostRecentJdk(condition: Sdk => Boolean): Option[Sdk] = {
    val jdkCondition = (sdk: Sdk) => sdk.getSdkType == JavaSdk.getInstance
    val combinedCondition = (sdk: Sdk) => sdk != null && condition(sdk) && jdkCondition(sdk)
    implicit def asCondition[A](f: A => Boolean): Condition[A] = (a: A) => f(a)
    Option(inReadAction(ProjectJdkTable.getInstance().findMostRecentSdk(combinedCondition)))
  }

  def mostRecentJdk: Option[Sdk] =
    findMostRecentJdk(_ => true)

  def defaultJavaLanguageLevelIn(jdk: Sdk): Option[LanguageLevel] = {
    // TODO either store or convert to 'match'
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
}
