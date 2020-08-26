package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTuplePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo._
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

// TODO: there are to many redundant TestNodeProvider.* method calls that actually check the same thing on same elements
//  eliminate this
final class UTestConfigurationProducer extends AbstractTestConfigurationProducer[UTestRunConfiguration] {

  override def getConfigurationFactory: ConfigurationFactory = UTestConfigurationType.instance.confFactory

  override def suitePaths: Seq[String] = UTestUtil.suitePaths

  override protected def configurationName(contextInfo: CreateFromContextInfo): String = contextInfo match {
    case AllInPackage(_, packageName)           =>
      ScalaBundle.message("test.in.scope.utest.presentable.text", packageName)
    case ClassWithTestName(testClass, testName) =>
      StringUtil.getShortName(testClass.qualifiedName) + testName.fold("")("\\" + _)
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
          case collection.Seq(name) =>
            val testName = TestConfigurationUtil.getStaticTestName(name)
            val testNameTotal = testName.flatMap(testName => buildTestPath(methodCall, testName))
            testNameTotal
          case _ => None
        }
      case _ => None
    }

  override def getTestClassWithTestName(location: PsiElementLocation): Option[ClassWithTestName] = {
    val element = location.getPsiElement

    //first, check that containing type definition is a uTest suite
    // TODO: eliminate code duplication
    var containingObject: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (containingObject == null)
      return None
    while (!containingObject.isInstanceOf[ScObject] && PsiTreeUtil.getParentOfType(containingObject, classOf[ScTypeDefinition], true) != null) {
      containingObject = PsiTreeUtil.getParentOfType(containingObject, classOf[ScTypeDefinition], true)
    }
    //this is checked once we provide a concrete class for test run
    //    if (!containingObject.isInstanceOf[ScObject]) return fail
    if (!suitePaths.exists(suitePath => TestConfigurationUtil.isInheritor(containingObject, suitePath)))
      return None

    import TestNodeProvider._
    val nameContainer = ScalaPsiUtil.getParentWithProperty(element, strict = false, p => {
        isUTestInfixExpr(p) ||
        isUTestSuiteApplyCall(p) ||
        isUTestApplyCall(p) ||
        isUTestObjectApplyCall(p) ||
        isUTestTestsCall(p)
    })
    val testNameOpt = nameContainer.flatMap {
      case infixExpr: ScInfixExpr                             => buildPathFromTestExpr(infixExpr) //test location is a scope defined through infix '-'
      case call: ScMethodCall if isUTestApplyCall(call)       => buildPathFromTestExpr(call) //test location is a scope define without use of '-' method
      case call: ScMethodCall if isUTestObjectApplyCall(call) => buildPathFromTestObjectApplyCall(call) // test("testName") {}
      case call: ScMethodCall                                 => getTestSuiteName(call) //test location is a test method definition
      case _                                                  => None
    }
    //it is also possible that element is on left-hand of test suite definition
    val testName = testNameOpt.orElse(TestNodeProvider.getUTestLeftHandTestDefinition(element).flatMap(getTestSuiteName))
    Some(ClassWithTestName(containingObject, testName))
  }
}
