package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.ConfigurationFactory
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import com.intellij.psi.{PsiModifierList, PsiClass}

class UTestRunConfiguration(override val project: Project,
                            override val configurationFactory: ConfigurationFactory,
                            override val name: String)
        extends AbstractTestRunConfiguration(project, configurationFactory, name)
        with ScalaTestingConfiguration {

  override protected[test] def isInvalidSuite(clazz: PsiClass): Boolean = {
    val list: PsiModifierList = clazz.getModifierList
    list != null && list.hasModifierProperty("abstract")
  }

  override def reporterClass: String = null

  override def suitePath = "utest.framework.TestSuite"

  override def mainClass = "org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner"

  override def errorMessage: String = "utest is not specified"

  override def currentConfiguration = UTestRunConfiguration.this
}
