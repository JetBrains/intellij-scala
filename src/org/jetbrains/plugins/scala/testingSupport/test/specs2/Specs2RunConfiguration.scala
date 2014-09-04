package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.test._


/**
 * @author Ksenia.Sautina
 * @since 5/17/12
 */

class Specs2RunConfiguration(override val project: Project,
                             override val configurationFactory: ConfigurationFactory,
                             override val name: String)
        extends AbstractTestRunConfiguration(project, configurationFactory, name)
        with ScalaTestingConfiguration {

  override def suitePath = "org.specs2.specification.SpecificationStructure"

  override def mainClass = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Runner"

  override def reporterClass = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Notifier"

  override def errorMessage: String = "Specs2 is not specified"

  override def currentConfiguration = Specs2RunConfiguration.this
}