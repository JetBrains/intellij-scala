package org.jetbrains.plugins.scala
package statistics

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector
import com.intellij.internal.statistic.beans.{UsageDescriptor, GroupDescriptor}
import com.intellij.openapi.project.Project
import java.util
import scala.collection.mutable
import com.intellij.openapi.module.{ModuleManager, ModuleUtil, ModuleUtilCore}
import org.jetbrains.plugins.scala.config.ScalaFacet

/**
 * @author Alefas
 * @since 03.03.14
 */
class ScalaApplicationUsagesCollector extends AbstractApplicationUsagesCollector {
  override def getProjectUsages(project: Project): util.Set[UsageDescriptor] = {
    val set: mutable.HashSet[UsageDescriptor] = new mutable.HashSet[UsageDescriptor]

    //collecting Scala version
    var scala_version: Option[String] = None
    for (module <- ModuleManager.getInstance(project).getModules) {
      ScalaFacet.findIn(module) match {
        case Some(facet) =>
          scala_version = Some(facet.version)
        case _ =>
      }
    }

    scala_version.foreach {
      case version: String => set += new UsageDescriptor(version, 1)
    }

    import scala.collection.JavaConversions._
    set

  }

  override def getGroupId: GroupDescriptor = new GroupDescriptor("Scala")
}