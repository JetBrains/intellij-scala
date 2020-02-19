package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.{PsiClass, PsiModifier}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiMethodExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScTypeExt, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author Ksenia.Sautina
  * @since 5/17/12
  */

class ScalaTestRunConfiguration(project: Project,
                                override val configurationFactory: ConfigurationFactory,
                                override val name: String)
  extends AbstractTestRunConfiguration(project, configurationFactory, name, TestConfigurationUtil.scalaTestConfigurationProducer) {

  override def suitePaths: List[String] = ScalaTestUtil.suitePaths

  override def runnerClassName = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner"

  override def reporterClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter"

  override def errorMessage: String = ScalaBundle.message("scalatest.config.scalatest.is.not.specified")

  override def currentConfiguration: ScalaTestRunConfiguration = ScalaTestRunConfiguration.this

  protected[test] override def isInvalidSuite(clazz: PsiClass): Boolean = getSuiteClass.fold(_ => true, ScalaTestRunConfiguration.isInvalidSuite(clazz, _))

  override def allowsSbtUiRun: Boolean = true

  override def modifySbtSettingsForUi(comm: SbtShellCommunication): Future[SettingMap] =
    modifySetting(SettingMap(), "testOptions", "test", "Test", """Tests.Argument(TestFrameworks.ScalaTest, "-oDU")""", comm, !_.contains("-oDU"))
      .flatMap(modifySetting(_, "parallelExecution", "test", "Test", "false", comm, !_.contains("false"), shouldSet = true))

  override protected def sbtTestNameKey = " -- -t "
}

object ScalaTestRunConfiguration extends SuiteValidityChecker {

  protected def wrapWithAnnotationFqn = "org.scalatest.WrapWith"

  protected[test] def lackConfigMapConstructor(clazz: PsiClass): Boolean = {
    implicit val project: ProjectContext = clazz.projectContext

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
              val psiManager = ScalaPsiManager.instance
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