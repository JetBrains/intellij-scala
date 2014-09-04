package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.test._

/**
 * @author Ksenia.Sautina
 * @since 5/17/12
 */

class ScalaTestRunConfiguration(override val project: Project,
                                override val configurationFactory: ConfigurationFactory,
                                override val name: String)
  extends AbstractTestRunConfiguration(project, configurationFactory, name)
  with ScalaTestingConfiguration {

  override def suitePath = "org.scalatest.Suite"

  override def mainClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner"

  override def reporterClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter"

  override def errorMessage: String = "ScalaTest is not specified"

  override def currentConfiguration = ScalaTestRunConfiguration.this
}
