package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiModifierList}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestConfigurationUtil}

import scala.annotation.tailrec

class UTestRunConfiguration(project: Project,
                            override val configurationFactory: ConfigurationFactory,
                            override val name: String)
        extends AbstractTestRunConfiguration(project, configurationFactory, name, TestConfigurationUtil.uTestConfigurationProducer) {

  override protected[test] def isInvalidSuite(clazz: PsiClass): Boolean = {
    if (!clazz.isInstanceOf[ScObject]) return true
    val list: PsiModifierList = clazz.getModifierList
    list != null && list.hasModifierProperty("abstract") || getSuiteClass.fold(_ => true, !ScalaPsiUtil.isInheritorDeep(clazz, _))
  }

  @tailrec
  private def getClassPath(currentClass: ScTypeDefinition, acc: String = ""): String = {
    val parentTypeDef = PsiTreeUtil.getParentOfType(currentClass, classOf[ScTypeDefinition], true)
    if (parentTypeDef == null) {
      currentClass.qualifiedName + acc
    } else {
      getClassPath(parentTypeDef, acc + (if (parentTypeDef.isObject) "$" else ".") + currentClass.getName)
    }
  }

  override protected def getClassFileNames(classes: scala.collection.mutable.HashSet[PsiClass]): Seq[String] =
    classes.map {
      case typeDef: ScTypeDefinition => getClassPath(typeDef)
      case aClass => aClass.qualifiedName
    }.toSeq

  override def reporterClass: String = null

  override def suitePaths: Seq[String] = UTestUtil.suitePaths

  override def runnerClassName = "org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner"

  override def errorMessage: String = ScalaBundle.message("utest.config.utest.is.not.specified")

  override def currentConfiguration: UTestRunConfiguration = UTestRunConfiguration.this

  override protected def sbtClassKey = " -- "

  override protected def sbtTestNameKey = ""

  override protected def escapeTestName(test: String): String = {
    test.stripPrefix("tests").replace("\\", ".")
  }

  override protected def escapeClassAndTest(input: String): String = {
    if (input.contains(" ")) s""""$input""""
    else input
  }
}
