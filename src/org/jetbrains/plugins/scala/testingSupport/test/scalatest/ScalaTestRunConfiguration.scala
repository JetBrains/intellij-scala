package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingConfiguration
import org.jetbrains.plugins.scala.testingSupport.test._
import com.intellij.psi.{PsiModifier, PsiClass}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScClass}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import com.intellij.psi.search.ProjectScope
import org.jetbrains.plugins.scala.lang.psi.types.{Conformance, ScParameterizedType, ScType}

/**
 * @author Ksenia.Sautina
 * @since 5/17/12
 */

class ScalaTestRunConfiguration(override val project: Project,
                                override val configurationFactory: ConfigurationFactory,
                                override val name: String)
    extends AbstractTestRunConfiguration(project, configurationFactory, name)
    with ScalaTestingConfiguration {

  override def suitePaths = List("org.scalatest.Suite")

  override def mainClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunner"

  override def reporterClass = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter"

  override def errorMessage: String = "ScalaTest is not specified"

  override def currentConfiguration = ScalaTestRunConfiguration.this

  protected[test] override def isInvalidSuite(clazz: PsiClass): Boolean = ScalaTestRunConfiguration.isInvalidSuite(clazz)
}

object ScalaTestRunConfiguration extends SuiteValidityChecker {

  protected def wrapWithAnnotationFqn = "org.scalatest.WrapWith"

  protected[test] def lackConfigMapConstructor(clazz: PsiClass): Boolean = {
    val project = clazz.getProject
    val constructors = clazz match {
      case c: ScClass => c.secondaryConstructors.filter(_.isConstructor).toList ::: c.constructor.toList
      case _ => clazz.getConstructors.toList
    }
    for (con <- constructors) {
      if (con.isConstructor && con.getParameterList.getParametersCount == 1) {
        con match {
          case owner: ScModifierListOwner =>
            if (owner.hasModifierProperty(PsiModifier.PUBLIC)) {
              val params = con.getParameterList.getParameters
              val firstParam = params(0)
              val psiManager = ScalaPsiManager.instance(project)
              val mapPsiClass = psiManager.getCachedClass(ProjectScope.getAllScope(project), "scala.collection.immutable.Map")
              val mapClass = ScType.designator(mapPsiClass)
              val paramClass = ScType.create(firstParam.getType, project)
              val conformanceType = paramClass match {
                case parameterizedType: ScParameterizedType => parameterizedType.designator
                case _ => paramClass
              }
              if (Conformance.conforms(mapClass, conformanceType))
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
        val annotation = classDef.hasAnnotation(wrapWithAnnotationFqn)
        annotation.isDefined
      case _ => false
    }
    if (hasConfigMapAnnotation) {
      lackConfigMapConstructor(clazz)
    } else {
      AbstractTestRunConfiguration.lackSuitableConstructor(clazz)
    }
  }
}