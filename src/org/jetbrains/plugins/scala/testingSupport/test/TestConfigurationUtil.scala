package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScInfixExpr, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Ksenia.Sautina
 * @since 5/22/12
 */

object TestConfigurationUtil {

  def packageSettings(element: PsiElement, location: Location[_ <: PsiElement],
                      confFactory: AbstractTestRunConfigurationFactory,
                      displayName: String): RunnerAndConfigurationSettings = {
    val pack: PsiPackage = element match { 
      case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
      case pack: PsiPackage => pack
    }
    if (pack == null) return null
    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
    val configuration = settings.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    configuration.setTestPackagePath(pack.getQualifiedName)
    configuration.setTestKind(TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE)
    configuration.setGeneratedName(displayName)
    configuration.setModule(location.getModule)
    configuration.initWorkingDir()
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
    settings
  }

  def isPackageConfiguration(element: PsiElement, configuration: RunConfiguration): Boolean = {
    val pack: PsiPackage = element match {
      case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
      case pack: PsiPackage => pack
    }
    if (pack == null) return false
    configuration match {
      case configuration: AbstractTestRunConfiguration =>
        configuration.getTestKind == TestRunConfigurationForm.TestKind.ALL_IN_PACKAGE &&
          configuration.getTestPackagePath == pack.getQualifiedName
      case _ => false
    }
  }

  def isInheritor(clazz: ScTemplateDefinition, fqn: String): Boolean = {
    val suiteClazz = ScalaPsiManager.instance(clazz.getProject).getCachedClass(clazz.getResolveScope, fqn)
    if (suiteClazz == null) return false
    ScalaPsiUtil.cachedDeepIsInheritor(clazz, suiteClazz)
  }

  def getStaticTestName(element: PsiElement, allowSymbolLiterals: Boolean = false): Option[String] = {
    element match {
      case literal: ScLiteral if literal.isString && literal.getValue.isInstanceOf[String] =>
        Some(escapeTestName(literal.getValue.asInstanceOf[String]))
      case literal: ScLiteral if allowSymbolLiterals && literal.isSymbol && literal.getValue.isInstanceOf[Symbol] =>
        Some(escapeTestName(literal.getValue.asInstanceOf[Symbol].name))
      case p: ScParenthesisedExpr => p.expr.flatMap(getStaticTestName(_, allowSymbolLiterals))
      case infixExpr: ScInfixExpr =>
        infixExpr.getInvokedExpr match {
          case refExpr: ScReferenceExpression if refExpr.refName == "+" =>
            getStaticTestName(infixExpr.lOp, allowSymbolLiterals).flatMap(left => getStaticTestName(infixExpr.rOp, allowSymbolLiterals).map(left + _))
          case _ => None
        }
      case refExpr: ScReferenceExpression if refExpr.getText == "+" =>
        getStaticTestName(refExpr.getParent, allowSymbolLiterals)
      case refExpr: ScReferenceExpression =>
        refExpr.advancedResolve.map(_.getActualElement) match {
          case Some(refPattern: ScReferencePattern) =>
            ScalaPsiUtil.nameContext(refPattern) match {
              case patternDef: ScPatternDefinition => patternDef.expr.flatMap(getStaticTestName(_, allowSymbolLiterals))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  def getStaticTestNameOrNothing(element: PsiElement, allowSymbolLiterals: Boolean = false) =
    getStaticTestName(element, allowSymbolLiterals).getOrElse("")

  def escapeTestName(testName: String) = testName.replace("\\", "\\\\").replace("\n", "\\n")
}
