package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.Location
import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationTypeUtil, RunConfiguration}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDirectory, PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTuplePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, SingleTestData}

// TODO: there are to many redundant TestNodeProvider.* method calls that actually check the same thing on same elements
//  eliminate this
class UTestConfigurationProducer extends AbstractTestConfigurationProducer[UTestRunConfiguration] {

  override def configurationFactory: ConfigurationFactory = {
    val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[UTestConfigurationType])
    configurationType.confFactory
  }

  override def suitePaths: Seq[String] = UTestUtil.suitePaths

  override protected def configurationNameForPackage(packageName: String): String = ScalaBundle.message("test.in.scope.utest.presentable.text", packageName)

  override protected def configurationName(testClass: ScTypeDefinition, testName: String): String =
    StringUtil.getShortName(testClass.qualifiedName) + (if (testName == null) "" else "\\" + testName)

  override def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element == null) return false
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      if (!configuration.isInstanceOf[UTestRunConfiguration]) return false
      return TestConfigurationUtil.isPackageConfiguration(element, configuration)
    }
    val (testClass, testName) = getTestClassWithTestName(location)
    if (testClass == null) return false
    val testClassPath = testClass.qualifiedName
    configuration match {
      case configuration: UTestRunConfiguration =>
        configuration.testConfigurationData match {
          case testData: SingleTestData => testData.testClassPath == testClassPath && testData.testName == testName
          case classData: ClassTestData => classData.testClassPath == testClassPath && testName == null
          case _ => false
        }
      case _ => false
    }
  }

  /**
    * Provides name of a test suite (i.e. single test defined as a val is uTest TestSuite) from a an 'apply' method call
    * defining the suite.
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
      case (_: ScInfixExpr | _: ScMethodCall) && Parent(block: ScBlockExpr) => block.getParent match {
        case argList: ScArguments =>
          argList.getParent match {
            case call: ScMethodCall if TestNodeProvider.isUTestSuiteApplyCall(call) || TestNodeProvider.isUTestTestsCall(call) =>
              getTestSuiteName(call).map(_ + "\\" + testScopeName)
            case call: ScMethodCall if TestNodeProvider.isUTestApplyCall(call) =>
              buildPathFromTestExpr(call).map(_ + "\\" + testScopeName)
            case call: ScMethodCall if TestNodeProvider.isUTestObjectApplyCall(call) =>
              buildPathFromTestObjectApplyCall(call).map(_ + "\\" + testScopeName)
            case _ => None
          }
        case upperInfix: ScInfixExpr =>
          buildPathFromTestExpr(upperInfix).map(_ + "\\" + testScopeName)
        case _ => None
      }
      case _ => None
    }
  }

  private def buildPathFromTestExpr(expr: ScExpression): Option[String] =
    for {
      child <- expr.firstChild
      testName <- TestConfigurationUtil.getStaticTestName(child, allowSymbolLiterals = true)
      testPath <- buildTestPath(expr, testName)
    } yield testPath


  // test("testMethodName") {}
  private def buildPathFromTestObjectApplyCall(methodCall: ScMethodCall): Option[String] =
    methodCall.deepestInvokedExpr.getParent match {
      case deepest: ScMethodCall =>
        deepest.args.exprs match {
          case Seq(name) =>
            val testName = TestConfigurationUtil.getStaticTestName(name)
            val testNameTotal = testName.flatMap(testName => buildTestPath(methodCall, testName))
            testNameTotal
          case _ => None
        }
      case _ => None
    }

  override def getTestClassWithTestName(location: Location[_ <: PsiElement]): (ScTypeDefinition, String) = {
    val element = location.getPsiElement
    val fail = (null, null)
    //first, check that containing type definition is a uTest suite
    // TODO: eliminate code duplication
    var containingObject: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (containingObject == null) return fail
    while (!containingObject.isInstanceOf[ScObject] && PsiTreeUtil.getParentOfType(containingObject, classOf[ScTypeDefinition], true) != null) {
      containingObject = PsiTreeUtil.getParentOfType(containingObject, classOf[ScTypeDefinition], true)
    }
    //this is checked once we provide a concrete class for test run
    //    if (!containingObject.isInstanceOf[ScObject]) return fail
    if (!suitePaths.exists(suitePath => TestConfigurationUtil.isInheritor(containingObject, suitePath))) return fail

    import TestNodeProvider._
    val nameContainer = ScalaPsiUtil.getParentWithProperty(element, strict = false, p => {
        isUTestInfixExpr(p) ||
        isUTestSuiteApplyCall(p) ||
        isUTestApplyCall(p) ||
        isUTestObjectApplyCall(p) ||
        isUTestTestsCall(p)
    })
    val testNameOpt = nameContainer.flatMap {
      case infixExpr: ScInfixExpr                                         => buildPathFromTestExpr(infixExpr) //test location is a scope defined through infix '-'
      case methodCall: ScMethodCall if isUTestApplyCall(methodCall)       => buildPathFromTestExpr(methodCall) //test location is a scope define without use of '-' method
      case methodCall: ScMethodCall if isUTestObjectApplyCall(methodCall) => buildPathFromTestObjectApplyCall(methodCall) // test("testName") {}
      case methodCall: ScMethodCall                                       => getTestSuiteName(methodCall) //test location is a test method definition
      case _                                                              => None
    }
    //it is also possible that element is on left-hand of test suite definition
    val testName = testNameOpt.orElse(TestNodeProvider.getUTestLeftHandTestDefinition(element).flatMap(getTestSuiteName)).orNull
    (containingObject, testName)
  }
}
