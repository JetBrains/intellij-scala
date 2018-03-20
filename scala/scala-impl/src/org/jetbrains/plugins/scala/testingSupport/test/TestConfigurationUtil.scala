package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2ConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestConfigurationProducer

/**
 * @author Ksenia.Sautina
 * @since 5/22/12
 */

object TestConfigurationUtil {

  def packageSettings(element: PsiElement, location: Location[_ <: PsiElement],
                      confFactory: AbstractTestRunConfigurationFactory,
                      displayName: String): RunnerAndConfigurationSettings = {
    val pack: PsiPackage = element match {
      case dir: PsiDirectory => JavaRuntimeConfigurationProducerBase.checkPackage(dir)
      case pack: PsiPackage => pack
    }
    if (pack == null) return null
    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
    val configuration = settings.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
    configuration.setTestConfigurationData(AllInPackageTestData(configuration, pack.getQualifiedName))
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
        configuration.getTestConfigurationData.isInstanceOf[AllInPackageTestData] &&
          configuration.getTestPackagePath == pack.getQualifiedName
      case _ => false
    }
  }

  def isInheritor(clazz: ScTemplateDefinition, fqn: String): Boolean = {
    val suiteClazz = ScalaPsiManager.instance(clazz.getProject).getCachedClass(clazz.resolveScope, fqn)
    suiteClazz.fold(false)(ScalaPsiUtil.isInheritorDeep(clazz, _))
  }

  private def getStaticTestNameElement(element: PsiElement, allowSymbolLiterals: Boolean): Option[Any] = {
    val noArgMethods = Seq("toLowerCase", "trim", "toString")
    val oneArgMethods = Seq("stripSuffix", "stripPrefix", "substring")
    val twoArgMethods = Seq("replace", "substring")

    def processNoArgMethods(refExpr: ScReferenceExpression) =
      if (refExpr.refName == "toString") {
        //special handling for now, since only toString is allowed on integers
        refExpr.smartQualifier.flatMap(getStaticTestNameElement(_, allowSymbolLiterals) match {
          case Some(string: String) => Some(string)
          case Some(number: Number) => Some(number.toString)
          case _ => None
        })
      } else refExpr.smartQualifier.
              flatMap(getStaticTestNameRaw(_, allowSymbolLiterals)).flatMap { expr =>
        refExpr.refName match {
          case "toLowerCase" => Some(expr.toLowerCase)
          case "trim" => Some(expr.trim)
          case "toString" => Some(expr)
          case _ => None
        }
      }

    element match {
      case literal: ScLiteral if literal.isString && literal.getValue.isInstanceOf[String] =>
        Some(escapeTestName(literal.getValue.asInstanceOf[String]))
      case literal: ScLiteral if allowSymbolLiterals && literal.isSymbol && literal.getValue.isInstanceOf[Symbol] =>
        Some(escapeTestName(literal.getValue.asInstanceOf[Symbol].name))
      case literal: ScLiteral if literal.getValue.isInstanceOf[Number] =>
        Some(literal.getValue)
      case p: ScParenthesisedExpr => p.innerElement.flatMap(getStaticTestNameRaw(_, allowSymbolLiterals))
      case infixExpr: ScInfixExpr =>
        infixExpr.getInvokedExpr match {
          case refExpr: ScReferenceExpression if refExpr.refName == "+" =>
            getStaticTestNameElement(infixExpr.left, allowSymbolLiterals).flatMap(left => getStaticTestNameElement(infixExpr.right, allowSymbolLiterals).map(left + _.toString))
          case _ => None
        }
      case methodCall: ScMethodCall =>
        methodCall.getInvokedExpr match {
          case refExpr: ScReferenceExpression if noArgMethods.contains(refExpr.refName) &&
            methodCall.argumentExpressions.isEmpty =>
            processNoArgMethods(refExpr)
          case refExpr: ScReferenceExpression if oneArgMethods.contains(refExpr.refName) &&
            methodCall.argumentExpressions.size == 1 =>
            def helper(anyExpr: Any, arg: Any): Option[Any] = (anyExpr, refExpr.refName, arg) match {
              case (expr: String, "stripSuffix", string: String) => Some(expr.stripSuffix(string))
              case (expr: String, "stripPrefix", string: String) => Some(expr.stripPrefix(string))
              case (expr: String, "substring", integer: Int) => Some(expr.substring(integer))
              case _ => None
            }
            methodCall.argumentExpressions.headOption.flatMap(getStaticTestNameElement(_, allowSymbolLiterals)).
              flatMap(arg =>
              refExpr.smartQualifier.flatMap(getStaticTestNameElement(_, allowSymbolLiterals)).flatMap(helper(_, arg))
              )
          case refExpr: ScReferenceExpression if twoArgMethods.contains(refExpr.refName) &&
            methodCall.argumentExpressions.size == 2 =>
            def helper(anyExpr: Any, arg1: Any, arg2: Any): Option[Any] = (anyExpr, refExpr.refName, arg1, arg2) match {
              case (expr: String, "replace", s1: String, s2: String) => Some(expr.replace(s1, s2))
              case (expr: String, "substring", begin: Int, end: Int) => Some(expr.substring(begin, end))
              case _ => None
            }
            val arg1Opt = getStaticTestNameElement(methodCall.argumentExpressions.head, allowSymbolLiterals)
            val arg2Opt = getStaticTestNameElement(methodCall.argumentExpressions(1), allowSymbolLiterals)
            (arg1Opt, arg2Opt) match {
              case (Some(arg1), Some(arg2)) =>
                refExpr.smartQualifier.flatMap(getStaticTestNameElement(_, allowSymbolLiterals)).flatMap(helper(_, arg1, arg2))
              case _ => None
            }
          case _ => None
        }
      case refExpr: ScReferenceExpression if refExpr.getText == "+" =>
        getStaticTestNameRaw(refExpr.getParent, allowSymbolLiterals)
      case refExpr: ScReferenceExpression if noArgMethods.contains(refExpr.refName) =>
        processNoArgMethods(refExpr)
      case refExpr: ScReferenceExpression =>
        refExpr.advancedResolve.map(_.getActualElement) match {
          case Some(refPattern: ScReferencePattern) =>
            ScalaPsiUtil.nameContext(refPattern) match {
              case patternDef: ScPatternDefinition => patternDef.expr.flatMap(getStaticTestNameRaw(_, allowSymbolLiterals))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  private def getStaticTestNameRaw(element: PsiElement, allowSymbolLiterals: Boolean): Option[String] =
    getStaticTestNameElement(element, allowSymbolLiterals).filter(_.isInstanceOf[String]).map(_.asInstanceOf[String])

  def getStaticTestName(element: PsiElement, allowSymbolLiterals: Boolean = false): Option[String] =
    getStaticTestNameRaw(element, allowSymbolLiterals).map(_.trim)

  def getStaticTestNameOrDefault(element: PsiElement, default: String, allowSymbolLiterals: Boolean): String =
    getStaticTestName(element, allowSymbolLiterals).getOrElse(default)

  def escapeTestName(testName: String): String = testName.replace("\\", "\\\\").replace("\n", "\\n")

  val specs2ConfigurationProducer = new Specs2ConfigurationProducer
  val uTestConfigurationProducer = new UTestConfigurationProducer
  val scalaTestConfigurationProducer = new ScalaTestConfigurationProducer
}
