package org.jetbrains.plugins.scala
package statistics

import java.util

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector
import com.intellij.internal.statistic.beans.{GroupDescriptor, UsageDescriptor}
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleUtil}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.util.PlatformUtils
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.settings.SbtSettings

/**
 * @author Alefas
 * @since 03.03.14
 */
class ScalaApplicationUsagesCollector extends AbstractProjectsUsagesCollector {
  override def getProjectUsages(project: Project): util.Set[UsageDescriptor] = {
    extensions.inReadAction {
      val set: util.HashSet[UsageDescriptor] = new util.HashSet[UsageDescriptor]()

      def addUsage(key: String): Unit = {
        set.add(new UsageDescriptor(key, 1))
      }

      def checkLibrary(qual: String, library: String) {
        if (JavaPsiFacade.getInstance(project).findPackage(qual) != null) {
          addUsage("Library: " + library)
        }
      }

      project.anyScalaModule
        .map(_.sdk)
        .flatMap(_.compilerVersion)
        .foreach(v => addUsage(s"Scala: $v"))

      project.modules.collectFirst {
        case SbtVersion(version) => addUsage(s"Sbt version: $version")
      }

      compilerPluginsUsed(project).foreach { name =>
        addUsage(s"Compiler plugin: $name")
      }

      val isPlayInstalled = PlatformUtils.isIdeaUltimate

      if (project.hasScala) {
        checkLibrary("org.apache.spark", "Spark")
        checkLibrary("io.prediction", "PredictionIO")
        checkLibrary("com.stratio.sparkta", "Sparkta")
        checkLibrary("cats", "cats")
        checkLibrary("com.twitter.finagle", "Finagle")
        checkLibrary("io.getquill", "Quill")
        checkLibrary("com.stratio.crossdata", "Crossdata")
        checkLibrary("io.finch", "Finch")
        checkLibrary("gitbucket", "GitBucket")
        checkLibrary("skinny", "Skinny-framework")
        checkLibrary("com.sksamuel.elastic4s", "Elastic4s")
        checkLibrary("securesocial", "SecureSocial")
        checkLibrary("com.github.dwhjames.awswrap", "AWSWrap")
        checkLibrary("com.github.mauricio.async.db.postgresql", "postgresql-async")
        checkLibrary("com.netflix.edda", "Edda")
        checkLibrary("redis", "Rediscala")
        checkLibrary("scalaz", "Scalaz")
        checkLibrary("org.scalatra", "Scalatra")
        checkLibrary("org.fusesource.scalate", "Scalate")
        checkLibrary("org.scalacheck", "Scalacheck")
        checkLibrary("com.twitter.scalding", "Scalding")
        checkLibrary("scalala.scalar", "Scalala")
        checkLibrary("scala.slick", "Slick")
        checkLibrary("org.scalatest", "ScalaTest")
        checkLibrary("scalax.io", "Scala.IO")
        checkLibrary("breeze.math", "Breeze")
        checkLibrary("com.foursquare.rogue", "Rouge")
        checkLibrary("shapeless", "Shapeless")
        checkLibrary("com.mongodb.casbah", "Casbah")
        checkLibrary("reactivemongo", "ReactiveMongo")
        checkLibrary("org.specs2", "Specs2")
        checkLibrary("play.api.mvc", s"Play2 for Scala|$isPlayInstalled")
        checkLibrary("akka.actor", "Akka for Scala")
        checkLibrary("utest", "uTest")
        checkLibrary("junit", "JUnit for Scala")
        checkLibrary("org.testng", "TestNG for Scala")
        checkLibrary("scala.scalajs", "ScalaJS")
        checkLibrary("net.liftweb", "Lift Framework")
        checkLibrary("spray", "Spray")
        checkLibrary("monocle", "Monocle")
        checkLibrary("com.twitter.util", "Twitter Util")

        project.modules.collectFirst {
          case JavaVersion(version) => addUsage(s"Java version: $version")
        }

      } else {
        checkLibrary("play.api.mvc", s"Play2 for Java|$isPlayInstalled")
        checkLibrary("akka.actor", "Akka for Java")
        checkLibrary("org.apache.spark", "Spark for Java")
      }

      set
    }
  }

  override def getGroupId: GroupDescriptor = GroupDescriptor.create("Scala")

  private object JavaVersion {
    def unapply(m: Module): Option[String] = {
      val manager = ModuleRootManager.getInstance(m)
      manager.getSdk match {
        case jsdk: Sdk if jsdk.getSdkType.isInstanceOf[JavaSdk] && jsdk.getVersionString != null =>
          Option(jsdk.getVersionString)
        case _ => None
      }
    }
  }

  private object SbtVersion {
    def unapply(m: Module): Option[String] = {
      SbtSettings.getInstance(m.getProject)
        .getLinkedProjectSettings(m)
        .safeMap(_.sbtVersion)
    }
  }

  private val compilerPluginHints: Map[String, String] = Map (
    "kind-projector" -> "Kind Projector",
    "paradise" -> "Macro/Meta paradise",
    "miniboxing" -> "Miniboxing"
  )

  def compilerPluginsUsed(p: Project): Set[String] = {
    val modules = ModuleUtil.getModulesOfType(p, JavaModuleType.getModuleType).asScala.filter(_.hasScala)

    val plugins = modules.flatMap(mod => mod.scalaCompilerSettings.plugins)
    var result = Set.empty[String]
    for {
      plugin <- plugins
      (hint, name) <- compilerPluginHints
      if plugin.contains(hint)
    } {
      result += name
    }
    result
  }
}
