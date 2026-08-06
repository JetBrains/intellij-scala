package org.jetbrains.plugins.scala

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.caches.stats.Tracer
import org.junit.Assert._

import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

package object macroAnnotations {
  def incModCount(project: Project): Unit = {
    val manager = PsiManager.getInstance(project)
    // TODO manager.getModificationTracker.asInstanceOf[PsiModificationTrackerImpl].incCounter()
    manager.dropPsiCaches()
  }

  def checkTracer(nameRegex: Regex, totalCount: Int, actualCount: Int)(body: => Unit): Unit = {
    Tracer.clearAll()
    Tracer.setEnabled(true)
    try {
      body
      checkTracerHas(nameRegex, totalCount, actualCount)
    } finally {
      Tracer.setEnabled(false)
    }
  }

  private def checkTracerHas(nameRegex: Regex, totalCount: Int, actualCount: Int): Unit = {
    val dataWithName = Tracer.getCurrentData.asScala.filter(data => nameRegex.matches(data.name))
    assertTrue(s"No tracer data with name matching ${nameRegex.regex} (${Tracer.getCurrentData.asScala.map(_.name).mkString(", ")})", dataWithName.nonEmpty)
    for {
      data <- dataWithName
    } {
      assertEquals("Wrong total count:", totalCount, data.totalCount)
      assertEquals("Wrong number of actual computations:", actualCount, data.actualCount)
    }
  }

  // Since JDK 21, inner lambdas have more elaborate binary names.
  def lambdaRegex(className: String, description: String): Regex = {
    def regexify(str: String): String = str.replace("$", "\\$").replace(".", "\\.")
    val r1 = regexify(className)
    val r2 = regexify(description)
    (r1 ++ "\\$\\$Lambda/0x([0-9a-f])+\\." ++ r2).r
  }
}
