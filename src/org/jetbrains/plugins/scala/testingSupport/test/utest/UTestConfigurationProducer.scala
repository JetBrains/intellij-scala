package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.{JavaRunConfigurationExtensionManager, Location, RunManager, RunnerAndConfigurationSettings}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDirectory, PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
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

  private val testObjectPath = "utest.framework.TestSuite$"

  private def checkMethod(call: ScMethodCall): Boolean = {
    call.getFirstChild match {
      case ref: ScReferenceExpression => ref.resolve() match {
        case member: ScMember =>
          val containingClass = member.getContainingClass
          containingClass.getQualifiedName == testObjectPath
        case _ => false
      }
      case _ => false
    }
  }

  @tailrec
  private def buildTestPath(test: ScLiteral, acc: String): Option[String] = {
    if (test.isString) {
      val infix = test.getParent
      infix match {
        case infixExpr: ScInfixExpr =>
          val block = infixExpr.getParent
          block match {
            case expr: ScBlockExpr =>
              expr.getParent match {
                case argList: ScArguments =>
                  val methodCall = argList.getParent
                  val testName = methodCall.getParent match {
                    case patDef: ScPatternDefinition => patDef.bindings.head.getName
                    case _ => return None
                  }
                  methodCall match {
                    case call: ScMethodCall =>
                      if (checkMethod(call)) Some(testName + "\\" + test.getValue.toString + acc) else None
                    case _ => None
                  }
                case upperInfix: ScInfixExpr =>
                  if (upperInfix.getFirstChild.isInstanceOf[ScLiteral]) {
                    buildTestPath(upperInfix.getFirstChild.asInstanceOf[ScLiteral], "\\" + test.getValue.toString + acc)
                  } else None
                case _ => None
              }
            case _ => None

          }
        case _ => None
      }
    } else None
  }

  override def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String) = {
    //for now, only support by-name calls if caret is in test's name
    val element = location.getPsiElement
    var parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    val parentLiteral: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (parent == null) return (null, null)

    while (PsiTreeUtil.getParentOfType(parent, classOf[ScTypeDefinition], true) != null) {
      parent = PsiTreeUtil.getParentOfType(parent, classOf[ScTypeDefinition], true)
    }
    if (!parent.isInstanceOf[ScObject]) return (null, null)
    if (!suitePaths.exists(suitePath => isInheritor(parent, suitePath))) return (null, null)
    val testClassPath = parent.qualifiedName

    //now get test name
    val testName = Option(parentLiteral) match {
      case Some(x) =>
        buildTestPath(x, "").orNull
      case None if element.isInstanceOf[LeafElement] =>
        val patDef: ScPatternDefinition = Option(element.getParent).map(_.getParent).map(_.getParent) match {
          case Some(pat: ScPatternDefinition) => pat
          case _ => null
        }
        //this can be used later, when creation click anywhere inside the test is supported
        //val patDef = PsiTreeUtil.getParentOfType(element, classOf[ScPatternDefinition])
        if (patDef == null) null else {
          val test = patDef.bindings.head.getName
          patDef.getLastChild match {
            case methodCall: ScMethodCall if checkMethod(methodCall) =>
              test
            case _ => null
          }
        }
      case _ => null
    }

    (testClassPath, testName)
  }
}
