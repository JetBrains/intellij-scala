package org.jetbrains.plugins.scala.project.external

import java.io.File

import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.extensions.inReadAction

import scala.language.implicitConversions


/**
 * @author Nikolay Obedin
 * @since 7/14/15.
 */
sealed abstract class SdkReference

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
          case JdkByName(version) => findMostRecentJdk(_.getName == version).orElse(findMostRecentJdk(_.getName.contains(version)))
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

  def defaultJavaLanguageLevelIn(jdk: Sdk): Option[LanguageLevel] =
    Option(LanguageLevel.parse(jdk.getVersionString))

  def javaLanguageLevelFrom(javacOptions: Seq[String]): Option[LanguageLevel] = {
    for {
      sourcePos <- Option(javacOptions.indexOf("-source")).filterNot(_ == -1)
      sourceValue <- javacOptions.lift(sourcePos + 1)
      languageLevel <- Option(LanguageLevel.parse(sourceValue))
    } yield languageLevel
  }
}
