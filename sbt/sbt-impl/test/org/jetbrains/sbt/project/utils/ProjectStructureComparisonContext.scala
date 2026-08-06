package org.jetbrains.sbt.project.utils

import com.intellij.openapi.project.{Project, ProjectUtil}
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext.AssertionFailStrategy

import scala.collection.mutable

case class ProjectStructureComparisonContext(
  options: ProjectComparisonOptions,
  macroSubstitutor: MacroSubstitutor,
  assertionFailStrategy: AssertionFailStrategy
) {
  def withOptions(optionsModifier: ProjectComparisonOptions => ProjectComparisonOptions): ProjectStructureComparisonContext = {
    val optionsNew = optionsModifier(options)
    new ProjectStructureComparisonContext(optionsNew, macroSubstitutor, assertionFailStrategy)
  }

  def withOptions(optionsNew: ProjectComparisonOptions): ProjectStructureComparisonContext = {
    new ProjectStructureComparisonContext(optionsNew, macroSubstitutor, assertionFailStrategy)
  }
}

object ProjectStructureComparisonContext {
  object Implicit {
    implicit def default(implicit project: Project): ProjectStructureComparisonContext = {
      val projectRoot = ProjectUtil.guessProjectDir(project)
      val substitutor = new MacroSubstitutor(Map(
        MacroSubstitutor.Keys.ProjectRoot -> projectRoot.getPath
      ))
      new ProjectStructureComparisonContext(
        ProjectComparisonOptions.Default,
        substitutor,
        //TODO: make CollectErrors the default for all tests, it's more convenient
        AssertionFailStrategy.FailImmediately
      )
    }
  }

  sealed trait AssertionFailStrategy {
    def run(body: => Unit): Unit
  }

  object AssertionFailStrategy {
    object FailImmediately extends AssertionFailStrategy {
      override def run(body: => Unit): Unit = body // throw any exceptions
    }
    final class CollectErrors extends AssertionFailStrategy {
      private val assertionErrors = mutable.ArrayBuffer[AssertionError]()

      def getAssertionErrors: Seq[AssertionError] = assertionErrors.toSeq

      override def run(body: => Unit): Unit = try {
        body
      } catch {
        case assertionError: AssertionError =>
          assertionErrors += assertionError
      }
    }
  }
}
