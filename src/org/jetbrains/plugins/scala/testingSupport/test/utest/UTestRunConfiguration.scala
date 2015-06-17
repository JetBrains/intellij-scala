package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration
import org.jetbrains.plugins.scala.extensions._

import scala.annotation.tailrec

class UTestRunConfiguration(override val project: Project,
                            override val configurationFactory: ConfigurationFactory,
                            override val name: String)
        extends AbstractTestRunConfiguration(project, configurationFactory, name)
        with ScalaTestingConfiguration {

  override protected[test] def isInvalidSuite(clazz: PsiClass): Boolean = {
    val list: PsiModifierList = clazz.getModifierList
    list != null && list.hasModifierProperty("abstract")
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

  override def suitePaths = List("utest.framework.TestSuite")

  override def mainClass = "org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner"

  override def errorMessage: String = "utest is not specified"

  override def currentConfiguration = UTestRunConfiguration.this
}
