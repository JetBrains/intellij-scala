package org.jetbrains.plugins.scala.util.runners

import org.junit.runner.Description
import org.junit.runner.manipulation.{Filter, Filterable}

import scala.jdk.CollectionConverters._

/**
 * Implements a filtering solution for a custom JUnit 4 test runner such that
 * individual tests from a parameterized suite can be run as usual.
 *
 * IntelliJ IDEA has built-in support only for the JUnit 4 [[org.junit.runners.Parameterized]] runner.
 * For other custom runners, (e.g. [[MultipleScalaVersionsJUnit4Runner]]) it is not able to run
 * individual tests because they are parameterized using custom logic.
 *
 * This filter fixes that deficiency.
 */
trait IdeaCustomParameterizedFilterable extends Filterable {

  abstract override def filter(filter: Filter): Unit = {
    val ideaFilter = new Filter {
      override def shouldRun(description: Description): Boolean = {
        // 1. If the standard filter accepts it (e.g., full suite run), let it pass.
        if (filter.shouldRun(description)) {
          return true
        }

        if (description.isTest) {
          // 2. If it's a test method, strip the suffix and test the bare name
          val methodName = description.getMethodName
          if (methodName != null && methodName.contains("[")) {
            val originalName = methodName.substring(0, methodName.indexOf('['))
            val strippedDesc = Description.createTestDescription(description.getClassName, originalName)
            return filter.shouldRun(strippedDesc)
          }
          false
        } else {
          // 3. If it's a Suite description (like our inner runners), check if ANY child passes
          description.getChildren.asScala.exists(shouldRun)
        }
      }

      override def describe(): String = filter.describe()
    }

    super.filter(ideaFilter)
  }
}
