package org.jetbrains.plugins.scala.traceLogViewer

import com.intellij.execution.filters.{CompositeFilter, ExceptionFilters}
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.traceLogger.protocol.StackTraceEntry

package object viewer {
  def gotoStackTraceEntry(entry: StackTraceEntry): Unit = {
    val goto = ProjectManager.getInstance()
      .getOpenProjects.iterator
      .flatMap { project =>
        val filter = new CompositeFilter(project, ExceptionFilters.getFilters(GlobalSearchScope.allScope(project)))
        val line = entry.toStackTraceElement.toString
        filter.applyFilter(line, line.length).toOption
          .flatMap(_.getFirstHyperlinkInfo.toOption)
          .map(_ -> project)
      }
      .nextOption()
    goto.foreach { case (link, project) => link.navigate(project) }
  }
}
