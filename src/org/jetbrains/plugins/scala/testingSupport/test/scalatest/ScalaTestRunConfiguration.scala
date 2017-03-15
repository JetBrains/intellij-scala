package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.{PsiClass, PsiModifier}
import org.jetbrains.plugins.scala.extensions.{PsiMethodExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScTypeExt, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Ksenia.Sautina
  * @since 5/17/12
  */

class ScalaTestRunConfiguration(override val project: Project,
                                override val configurationFactory: ConfigurationFactory,
                                override val name: String)
  extends AbstractTestRunConfiguration(project, configurationFactory, name, TestConfigurationUtil.scalaTestConfigurationProducer) {

  override def suitePaths: List[String] = ScalaTestUtil.suitePaths

  override def mainClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner"

  override def reporterClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter"

  override def errorMessage: String = "ScalaTest is not specified"

  override def currentConfiguration: ScalaTestRunConfiguration = ScalaTestRunConfiguration.this

  protected[test] override def isInvalidSuite(clazz: PsiClass): Boolean = ScalaTestRunConfiguration.isInvalidSuite(clazz, getSuiteClass)

  override def allowsSbtUiRun: Boolean = true

  override def modifySbtSettingsForUi(comm: SbtShellCommunication): Future[Option[SettingMap]] = {
    modifySetting(SettingMap(), "testOptions", "Test", "Tests.Argument(TestFrameworks.ScalaTest, \"-oDU\")",
      comm, !_.contains("-oDU")) flatMap { _.map(
      modifySetting(_, "parallelExecution", "Test", "false", comm, !_.contains("false"), shouldSet = true)
    ).getOrElse(Future[Option[SettingMap]](None))}
  }

  override protected def sbtTestNameKey = " -- -t "
}

object ScalaTestRunConfiguration extends SuiteValidityChecker {

  protected def wrapWithAnnotationFqn = "org.scalatest.WrapWith"

  protected[test] def lackConfigMapConstructor(clazz: PsiClass): Boolean = {
    val project = clazz.getProject
    implicit val typeSystem = project.typeSystem
    val constructors = clazz match {
      case c: ScClass => c.secondaryConstructors.filter(_.isConstructor).toList ::: c.constructor.toList
      case _ => clazz.getConstructors.toList
    }
    for (con <- constructors) {
      if (con.isConstructor && con.getParameterList.getParametersCount == 1) {
        con match {
          case owner: ScModifierListOwner =>
            if (owner.hasModifierProperty(PsiModifier.PUBLIC)) {
              val params = con.parameters
              val firstParam = params.head
              val psiManager = ScalaPsiManager.instance(project)
              val mapPsiClass = psiManager.getCachedClass(ProjectScope.getAllScope(project), "scala.collection.immutable.Map").orNull
              val mapClass = ScalaType.designator(mapPsiClass)
              val paramClass = firstParam.getType.toScType()
              val conformanceType = paramClass match {
                case parameterizedType: ScParameterizedType => parameterizedType.designator
                case _ => paramClass
              }
              if (conformanceType.conforms(mapClass))
                return false
            }
          case _ =>
        }
      }
    }
    true
  }

  override protected[test] def lackSuitableConstructor(clazz: PsiClass): Boolean = {
    val hasConfigMapAnnotation = clazz match {
      case classDef: ScTypeDefinition =>
        classDef.hasAnnotation(wrapWithAnnotationFqn)
      case _ => false
    }
    if (hasConfigMapAnnotation) {
      lackConfigMapConstructor(clazz)
    } else {
      AbstractTestRunConfiguration.lackSuitableConstructor(clazz)
    }
  }
}