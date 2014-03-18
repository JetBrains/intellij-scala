package org.jetbrains.plugins.scala
package statistics

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector
import com.intellij.internal.statistic.beans.{UsageDescriptor, GroupDescriptor}
import com.intellij.openapi.project.Project
import java.util
import scala.collection.mutable
import com.intellij.openapi.module.{ModuleManager, ModuleUtil, ModuleUtilCore}
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.projectRoots.{JavaSdk, JdkUtil}
import com.intellij.openapi.roots.ModuleRootManager

/**
 * @author Alefas
 * @since 03.03.14
 */
class ScalaApplicationUsagesCollector extends AbstractApplicationUsagesCollector {
  override def getProjectUsages(project: Project): util.Set[UsageDescriptor] = {
    val set: mutable.HashSet[UsageDescriptor] = new mutable.HashSet[UsageDescriptor]

    //collecting Scala version
    var scala_version: Option[String] = None
    var java_version: Option[String] = None
    for (module <- ModuleManager.getInstance(project).getModules) {
      ScalaFacet.findIn(module) match {
        case Some(facet) =>
          scala_version = Some(facet.version)
        case _ =>
      }

      ModuleRootManager.getInstance(module).getSdk match {
        case jsdk: JavaSdk => java_version = Option(jsdk.getVersionString)
        case _ =>
      }
    }

    scala_version.foreach {
      case version: String => set += new UsageDescriptor(s"Scala: $version", 1)
    }

    def checkLibrary(qual: String, library: String) {
      if (JavaPsiFacade.getInstance(project).findPackage(qual) != null) {
        set += new UsageDescriptor("Library: " + library, 1)
      }
    }


    val isPlayInstalled = PluginManager.isPluginInstalled(PluginId.getId("com.intellij.scala.play2template"))

    if (scala_version.isDefined) {
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

      java_version.foreach {
        case version: String => set += new UsageDescriptor(s"Java version: $version", 1)
      }
    } else {
      checkLibrary("play.api.mvc", s"Play2 for Java|$isPlayInstalled")
      checkLibrary("akka.actor", "Akka for Java")
    }

    import scala.collection.JavaConversions._
    set

  }

  override def getGroupId: GroupDescriptor = GroupDescriptor.create("Scala")
}