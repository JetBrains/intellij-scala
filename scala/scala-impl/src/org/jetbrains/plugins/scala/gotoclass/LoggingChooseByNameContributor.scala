package org.jetbrains.plugins.scala.gotoclass

import com.intellij.navigation.{ChooseByNameContributor, NavigationItem}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.{DumbService, Project}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.gotoclass.LoggingChooseByNameContributor.LOG

import scala.jdk.CollectionConverters.asScalaBufferConverter

class LoggingChooseByNameContributor extends ChooseByNameContributor {
  override def getNames(project: Project, includeNonProjectItems: Boolean): Array[String] = {
    if (LOG.isDebugEnabled) {
      logAllContributors(project, s"getNames(includeNonProjectItems = $includeNonProjectItems)") {
        _.getNames(project, includeNonProjectItems)
      }
    }

    Array.empty
  }

  override def getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array[NavigationItem] =
    Array.empty

  private def logResult[T](contributor: ChooseByNameContributor, results: Array[T]): Unit = {
    val contributorName = contributor.getClass.getSimpleName
    val limit = 5
    val suffix = if (results.length > limit) ", ...]" else "]"
    val firstResults = results.take(limit).mkString("[", ", ", suffix)
    LOG.debug(s"  $contributorName found ${results.length}: $firstResults ")
  }

  private def logAllContributors(project: Project, title: String)
                                (resultsFunction: ChooseByNameContributor => Array[String]): Unit = {
    val isDumb = DumbService.isDumb(project)

    def contributors(ep: ExtensionPointName[ChooseByNameContributor]) =
      if (isDumb) DumbService.getDumbAwareExtensions(project, ep)
      else ep.getExtensionList

    import ChooseByNameContributor._

    LOG.debug(s"$title, dumbMode: $isDumb")

    for {
      ep <- Seq(CLASS_EP_NAME, FILE_EP_NAME)
      contributor <- contributors(ep).asScala
      if !contributor.is[LoggingChooseByNameContributor]
    } {
      val results = resultsFunction(contributor)

      logResult(contributor, results)
    }
  }
}

object LoggingChooseByNameContributor {
  private val LOG = Logger.getInstance(classOf[LoggingChooseByNameContributor])
}
