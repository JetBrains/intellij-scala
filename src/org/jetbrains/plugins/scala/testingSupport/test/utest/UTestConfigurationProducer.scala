package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDirectory, PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTuplePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationProducer, TestConfigurationUtil}

import scala.annotation.tailrec

class UTestConfigurationProducer extends {
  val confType = new UTestConfigurationType
  val confFactory = confType.confFactory
} with TestConfigurationProducer(confType) with AbstractTestConfigurationProducer {

  override def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element == null) return false
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      if (!configuration.isInstanceOf[UTestRunConfiguration]) return false
      return TestConfigurationUtil.isPackageConfiguration(element, configuration)
    }
    val (testClassPath, testClassName) = getLocationClassAndTest(location)
    if (testClassPath == null) return false
    configuration match {
      case configuration: UTestRunConfiguration if configuration.getTestKind == TestKind.CLASS &&
        testClassName == null =>
        testClassPath == configuration.getTestClassPath
      case configuration: UTestRunConfiguration if configuration.getTestKind == TestKind.TEST_NAME =>
        testClassPath == configuration.getTestClassPath && testClassName != null &&
          testClassName == configuration.getTestName
      case _ => false
    }
  }

  override def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    if (element == null) return null

    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val name = element match {
        case p: PsiPackage => p.getName
        case d: PsiDirectory => d.getName
      }
      return TestConfigurationUtil.packageSettings(element, location, confFactory, ScalaBundle.message("test.in.scope.utest.presentable.text", name))
    }

    val (testClassPath, testName) = getLocationClassAndTest(location)
    if (testClassPath == null) return null
    val settings = RunManager.getInstance(location.getProject).
      createRunConfiguration(StringUtil.getShortName(testClassPath) +
      (if (testName != null) "\\" + testName else ""), confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[UTestRunConfiguration]
    runConfiguration.setTestClassPath(testClassPath)
    runConfiguration.initWorkingDir()
    if (testName != null) runConfiguration.setTestName(testName)
    val kind = if (testName == null) TestKind.CLASS else TestKind.TEST_NAME
    runConfiguration.setTestKind(kind)
    try {
      val module = ScalaPsiUtil.getModule(element)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }
    catch {
      case e: Exception =>
    }
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(runConfiguration, location)
    settings
  }

  override def suitePaths = List("utest.framework.TestSuite")

  /**
   * Provides name of a test suite (i.e. single test defined as a val is uTest TestSuite) from a an 'apply' method call
   * defining the suite.
   * @param testSuite
   * @return
   */
  private def getTestSuiteName(testSuite: ScMethodCall): Option[String] = {
    testSuite.getParent match {
      case patternDef: ScPatternDefinition => Some(patternDef.bindings.head.getName)
      case tuple: ScTuple =>
        tuple.getParent match {
          case patternDef: ScPatternDefinition =>
            val patterns = patternDef.pList.patterns
            if (patterns.size == 1 && patterns.head.isInstanceOf[ScTuplePattern]) {
              val index = tuple.exprs.zipWithIndex.find { case (expr, _) => expr == testSuite }.map(_._2).get
              val bindings = patternDef.bindings
              if (bindings.size > index) Some(bindings(index).getName) else None
            } else None
          case _ => None
        }
      case _ => None
    }
  }

  private def buildTestPath(testExpr: ScExpression, testScopeName: String): Option[String] = {
    testExpr match {
      case (_: ScInfixExpr) | (_: ScMethodCall) =>
        testExpr.getParent match {
          case block: ScBlockExpr =>
            block.getParent match {
              case argList: ScArguments =>
                argList.getParent match {
                  case call: ScMethodCall if TestNodeProvider.isUTestSuiteApplyCall(call) =>
                    getTestSuiteName(call).map(_ + "\\" + testScopeName)
                  case call: ScMethodCall if TestNodeProvider.isUTestApplyCall(call) =>
                    buildPathFromTestExpr(call).map(_ + "\\" + testScopeName)
                  case _ => None
                }
              case upperInfix: ScInfixExpr =>
                buildPathFromTestExpr(upperInfix).map(_ + "\\" + testScopeName)
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  private def getTestName(literal: ScLiteral) = literal.getValue match {
    case symbol: Symbol => symbol.name
    case other => other.toString
  }

  /**
   * Attempts to build a test path from given expression containing test name and expression containing test scope definition.
   * @param testNameExpr expression containing test name
   * @param testExpr expression containing test definition
   * @return
   */
  @tailrec
  private def buildPathFromTestNameExpr(testNameExpr: ScExpression, testExpr: ScExpression): Option[String] = {
    val subExprs = testNameExpr.getChildren.filter(_.isInstanceOf[ScExpression]).map(_.asInstanceOf[ScExpression])
    //for now, only process tests that have simple literals as names
    if (subExprs.length == 1) {
      subExprs.head match {
        case literal: ScLiteral if literal.isString || literal.isSymbol => buildTestPath(testExpr, getTestName(literal))
        case other => buildPathFromTestNameExpr(other, testExpr)
      }
    } else None
  }

  private def buildPathFromTestExpr(expr: ScExpression): Option[String] =
    expr.firstChild.flatMap(TestConfigurationUtil.getStaticTestName(_, allowSymbolLiterals = true)).
      flatMap(buildTestPath(expr, _))
  //
  //    expr.firstChild match {
  //      case Some(literal: ScLiteral) => buildTestPath(expr, getTestName(literal))
  //      case Some(innerExpr: ScExpression) => buildPathFromTestNameExpr(innerExpr, expr)
  //      case _ => None
  //    }

  override def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String) = {
    val element = location.getPsiElement
    val fail = (null, null)
    //first, check that containing type definition is a uTest suite
    var containingObject: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (containingObject == null) return fail
    while (!containingObject.isInstanceOf[ScObject] && PsiTreeUtil.getParentOfType(containingObject, classOf[ScTypeDefinition], true) != null) {
      containingObject = PsiTreeUtil.getParentOfType(containingObject, classOf[ScTypeDefinition], true)
    }
    if (!containingObject.isInstanceOf[ScObject]) return fail
    if (!suitePaths.exists(suitePath => TestConfigurationUtil.isInheritor(containingObject, suitePath))) return (null, null)
    val testClassPath = containingObject.qualifiedName

    val testName = ScalaPsiUtil.getParentWithProperty(element, strict = false,
      e => TestNodeProvider.isUTestInfixExpr(e) || TestNodeProvider.isUTestSuiteApplyCall(e) || TestNodeProvider.isUTestApplyCall(e)).
      map { case infixExpr: ScInfixExpr =>
      //test location is a scope defined through infix '-'
      buildPathFromTestExpr(infixExpr)
    case methodCall: ScMethodCall if TestNodeProvider.isUTestApplyCall(methodCall) =>
      //test location is a scope define without use of '-' method
      buildPathFromTestExpr(methodCall)
    case methodCall: ScMethodCall =>
      //test location is a test method definition
      getTestSuiteName(methodCall)
    case _ => None
    }.flatten.getOrElse(
        //it is also possible that element is on left-hand of test suite definition
        TestNodeProvider.getUTestLeftHandTestDefinition(element).flatMap(getTestSuiteName).orNull
      )
    (testClassPath, testName)
  }
}
