package org.jetbrains.plugins.scala.testingSupport
package test.structureView

import com.intellij.execution.PsiLocation
import com.intellij.icons.AllIcons
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.project.{IndexNotReadyException, Project}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, ScalaElementType}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScTuplePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScPatternsImpl
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.structureView.element.{Test, TypeDefinition, Value}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo.ClassWithTestName
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitUtils
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestUtil
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2Util
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestConfigurationProducer

import java.util.Collections
import java.{util => ju}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

// used on "Structure view" (Alt + 7)
// TODO: split this by test frameworks
class TestNodeProvider extends FileStructureNodeProvider[TreeElement] {
  override def getCheckBoxText: String = TestingSupportBundle.message("test.node.provider.show.scala.tests")

  override def getShortcut: Array[Shortcut] = Array[Shortcut]()

  //TODO: what icon should be used here?
  override def getPresentation: ActionPresentation = new ActionPresentationData(
    getCheckBoxText, null, AllIcons.Hierarchy.Supertypes)

  override def getName: String = "SCALA_SHOW_SCALATEST_TESTS"

  private def emptyList: ju.List[TreeElement] = Collections.emptyList[TreeElement]

  override def provideNodes(node: TreeElement): ju.Collection[TreeElement] =
    node match {
      case td: TypeDefinition =>
        val result = try {
          collectNodes(td)
        } catch {
          case _: IndexNotReadyException =>
            None
        }
        result.map(_.asJava).getOrElse(emptyList)

      case valElement: Value =>
        def tryTupledId(psiElement: PsiElement): ju.Collection[TreeElement] =
          TestNodeProvider.getUTestLeftHandTestDefinition(psiElement) match {
            case Some(testTupleDefinition) => TestNodeProvider.extractUTest(testTupleDefinition, testTupleDefinition.getProject)
            case _ => emptyList
          }

        valElement.getValue match {
          case valDef: ScPatternDefinition =>
            valDef.getLastChild match {
              case testCall: ScMethodCall =>
                TestNodeProvider.extractUTest(testCall, testCall.getProject)
              case _ => tryTupledId(valElement.element)
            }
          case named: ScNamedElement =>
            tryTupledId(named.nameId)
          case _ =>
            emptyList
        }
      case _ =>
        emptyList
    }

  private def collectNodes(td: TypeDefinition): Option[Seq[TreeElement]] = {
    val clazz = td.element
    if (clazz.isValid) {
      val templateBody = clazz.extendsBlock.templateBody
      templateBody.map(collectTestNodes(clazz, _))
    } else
      None
  }

  private def collectTestNodes(clazz: ScTypeDefinition, body: ScTemplateBody): Seq[TreeElement] =
    (for {
      expr <- body.children.filter(_.is[ScExpression])
      testNode <- testNodeForTemplateBodyChild(clazz, expr)
    } yield testNode).toSeq

  private def testNodeForTemplateBodyChild(clazz: ScTypeDefinition, expr: PsiElement) =
    expr match {
      case expr: ScMethodCall        => TestNodeProvider.extractTestViewElement(expr, clazz, clazz.getProject)
      case expr: ScInfixExpr         => TestNodeProvider.extractTestViewElementInfix(expr, clazz, clazz.getProject)
      case expr: ScPatternDefinition => TestNodeProvider.extractTestViewElementPatternDef(expr, clazz)
      case _                         => None
    }
}

object TestNodeProvider {
  @NonNls val IgnoredSuffix: String = " !!! IGNORED !!!"
  @NonNls val PendingSuffix: String = " (pending)"

  private def getInnerInfixExprs(expr: ScInfixExpr) = {
    expr.getLastChild.getChildren.filter(_.isInstanceOf[ScInfixExpr]).map(_.asInstanceOf[ScInfixExpr])
  }

  private def extractTestViewElementInfix(expr: ScInfixExpr, clazz: ScTypeDefinition, project: Project): Option[Test] = {
    import ScalaTestUtil._
    import Specs2Util._
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (flatSpecBases.exists(isInheritor(clazz, _)))
      extractFlatSpec(expr)
    else if (freeSpecBases.exists(isInheritor(clazz, _)))
      extractFreeSpec(expr, project)
    else if (wordSpecBases.exists(isInheritor(clazz, _)))
      extractWordSpec(expr, project)
    else if (isInheritor(clazz, unitSpecBase))
      extractUnitSpec(expr, project)
    else
      None
  }

  private def extractTestViewElementPatternDef(pDef: ScPatternDefinition, clazz: ScTypeDefinition): Option[Test] = {
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (isInheritor(clazz, "utest.framework.TestSuite") && pDef.getLastChild.isInstanceOf[ScMethodCall]) {
      val methodCall = pDef.getLastChild.asInstanceOf[ScMethodCall]
      checkScMethodCall(methodCall, "apply")
      None
    }
    else None
  }

  private def extractTestViewElement(expr: ScMethodCall, clazz: ScTypeDefinition, project: Project): Option[Test] = {
    import ScalaTestUtil._
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (funSuiteBases.exists(isInheritor(clazz, _)))
      extractFunSuite(expr) //this should be a funSuite-like test
    else if (featureSpecBases.exists(isInheritor(clazz, _)))
      extractFeatureSpec(expr, project)
    else if (funSpecBasesPost2_0.exists(isInheritor(clazz, _)) || funSpecBasesPre2_0.exists(isInheritor(clazz, _)))
      extractFunSpec(expr, project)
    else if (propSpecBases.exists(isInheritor(clazz, _)))
      extractPropSpec(expr)
    else if (isInheritor(clazz, MUnitUtils.BaseFunSuiteFqn) || isInheritor(clazz, MUnitUtils.FunSuiteFqn))
      extractMUnitFunSuite(expr) //this should be a funSuite-like test
    else
      None
  }

  // TODO: refactor usage of these, it looks ugly, many code duplications, error-prone, probably non-efficient
  //ScalaTest < 3.0.3 does not support scala 2.13, no need du duplicate param clauses with `immutable`
  private val scMethodCallDefaultArg = Seq(List("java.lang.String", "scala.collection.Seq<org.scalatest.Tag>"), List("void"))
  private val scMethodCallDefaultArgScalaTest3_v1 = Seq(List("java.lang.String", "scala.collection.Seq<org.scalatest.Tag>"), List("java.lang.Object")) // scala < 2.13
  private val scMethodCallDefaultArgScalaTest3_v2 = Seq(List("java.lang.String", "scala.collection.immutable.Seq<org.scalatest.Tag>"), List("java.lang.Object")) // scala >= 2.13

  private def getMethodCallTestName(expr: ScMethodCall) =
  //TODO: this is horrible
    expr.getNode.getFirstChildNode.getText

  private def processChildren[T <: PsiElement](children: Array[T],
                                               processFun: (T, Project) => Option[TreeElement],
                                               project: Project): Array[TreeElement] = {
    children.map(element => processFun(element, project)).filter(_.isDefined).map(_.get)
  }

  private def getInnerMethodCalls(expr: ScMethodCall) = {
    expr.args.getLastChild.getChildren.filter(_.isInstanceOf[ScMethodCall]).map(_.asInstanceOf[ScMethodCall])
  }

  private def getInnerExprs(expr: PsiElement) = {
    (expr match {
      case _: ScInfixExpr => expr.getLastChild.getChildren
      case methodCall: ScMethodCall => methodCall.args.getLastChild.getChildren
      case _ => Array[ScExpression]()
    }).filter(_.isInstanceOf[ScExpression]).map(_.asInstanceOf[ScExpression])
  }

  private def checkMethodCallPending(expr: ScMethodCall): Boolean = {
    expr.getLastChild match {
      case args: ScArgumentExprList if args.getChildren.length == 1 =>
        args.getChildren.head match {
          case lastChild: ScReferenceExpression => checkRefExpr(lastChild, "pending")
          case _ => false
        }
      case _ => false
    }
  }

  private def ignoredScalaTestElement(element: PsiElement, name: String, children: Array[TreeElement] = Array()) =
    new Test(element, name + TestNodeProvider.IgnoredSuffix, children, Test.IgnoredStatusId)

  private def pendingScalaTestElement(element: PsiElement, name: String, children: Array[TreeElement] = Array()) =
    new Test(element, name + TestNodeProvider.PendingSuffix, children, Test.PendingStatusId)

  def getInfixExprTestName(expr: ScInfixExpr): String = expr.getNode.getFirstChildNode.getText

  private def checkScMethodCall(expr: ScMethodCall, funName: String, paramNames: List[String]*): Boolean = {
    val methodExpr = expr.getEffectiveInvokedExpr.findFirstChildByType(ScalaElementType.REFERENCE_EXPRESSION)
    methodExpr.exists(methodExpr => checkRefExpr(methodExpr.asInstanceOf[ScReferenceExpression], funName, paramNames: _*))
  }

  private def checkScMethodCallApply(expr: ScMethodCall, @NonNls callerName: String, @NonNls paramNames: List[String]*): Boolean = {
    val methodExpr = expr.getEffectiveInvokedExpr match {
      case refExpr: ScReferenceExpression => Some(refExpr)
      case otherExpr => otherExpr.findFirstChildByTypeScala[ScReferenceExpression](ScalaElementType.REFERENCE_EXPRESSION)
    }
    methodExpr.exists { methodExpr =>
      methodExpr.bind() match {
        case Some(srr @ ScalaResolveResult(fun: ScFunctionDefinition, _)) if fun.name == CommonNames.Apply =>
          srr.getActualElement.name == callerName &&
            checkClauses(fun.getParameterList.clauses, paramNames)
        case _ => false
      }
    }
  }

  private def checkClauses(clauses: Seq[ScParameterClause], paramNames: Seq[Seq[String]]): Boolean = {
    val filteredClauses = clauses.filterNot(_.parameters.forall(_.isImplicitParameter))
    filteredClauses.length == paramNames.length && (filteredClauses zip paramNames).forall {
      case (clause, names) =>
        clause.parameters.length == names.length && (clause.parameters zip names).forall {
          case (param, name) => param.getType.getCanonicalText == name
        }
    }
  }

  private def checkScInfixExpr(expr: ScInfixExpr, funName: String, paramNames: Option[Seq[List[String]]]): Boolean = {
    val methodExpr = expr.getEffectiveInvokedExpr
    methodExpr.isInstanceOf[ScReferenceExpression] && checkRefExpr(methodExpr.asInstanceOf[ScReferenceExpression],
      funName, None, paramNames)
  }

  private def checkScInfixExpr(expr: ScInfixExpr, funName: String, paramNames: List[String]*): Boolean =
    checkScInfixExpr(expr, funName, Some(paramNames))

  private def checkRefExpr(refExpr: ScReferenceExpression, funName: String, paramNames: List[String]*): Boolean =
    checkRefExpr(refExpr, funName, None, Some(paramNames))

  private def checkRefExpr(
    refExpr: ScReferenceExpression,
    funName: String,
    funDef: Option[ScFunctionDefinition],
    paramNames: Option[Seq[List[String]]]
  ): Boolean = {
    val funDef2 = funDef.orElse(refExpr.resolve() match {
      case funDef: ScFunctionDefinition => Some(funDef)
      case _                            => None
    })
    funDef2.exists { funDef =>
      funDef.getName == funName &&
        paramNames.forall(checkClauses(funDef.getParameterList.clauses, _))
    }
  }

  private def checkSpecsPending(expr: ScInfixExpr): Boolean = {
    (expr.getLastChild match {
      case refExpr: ScReferenceExpression =>
        Some(refExpr)
      case methodCall: ScMethodCall =>
        methodCall.getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression => Some(ref)
          case otherExpr =>
            otherExpr.findFirstChildByType(ScalaElementType.REFERENCE_EXPRESSION).
              map(_.asInstanceOf[ScReferenceExpression])
        }
      case _ => None
    }).exists(refExpr => checkRefExpr(refExpr, "pendingUntilFixed", List("java.lang.String")) || checkRefExpr
    (refExpr, "pendingUntilFixed"))
  }

  private def extractScalaTestScInfixExpr(expr: ScInfixExpr, entry: ExtractEntry):
  Option[Test] = {
    if (entry.canIgnore && (checkScInfixExpr(expr, "ignore", List("void")) ||
      checkScInfixExpr(expr, "ignore", List("java.lang.Object")) || checkIgnoreExpr(expr))) {
      Some(ignoredScalaTestElement(expr, getInfixExprTestName(expr), entry.children(())))
    } else if (checkScInfixExpr(expr, "is", List("org.scalatest.PendingNothing")) || checkPendingInfixExpr(expr)) {
      Some(pendingScalaTestElement(expr, getInfixExprTestName(expr), entry.children(())))
    } else if (checkScInfixExpr(expr, entry.funName, entry.args: _*)) {
      Some(new Test(expr, getInfixExprTestName(expr), entry.children(())))
    } else None
  }

  private def checkPendingInfixExpr(expr: ScInfixExpr): Boolean = {
    expr.getLastChild match {
      case expr: ScExpression => checkPendingExpr(expr)
      case _ => false
    }
  }

  private def checkPendingExpr(expr: ScExpression): Boolean = {
    val refExpr = expr match {
      case refExpr: ScReferenceExpression => Some(refExpr)
      case _ => expr.findLastChildByTypeScala(ScalaElementType.REFERENCE_EXPRESSION)
    }
    refExpr.exists(checkRefExpr(_, "pending"))
  }

  private def checkIgnoreExpr(expr: ScInfixExpr): Boolean = {
    expr.getFirstChild match {
      case infixExpr: ScInfixExpr if infixExpr.getFirstChild.isInstanceOf[ScReferenceExpression] =>
        infixExpr.getFirstChild.asInstanceOf[ScReferenceExpression].resolve() match {
          case pattern: ScReferencePattern if pattern.getName == "ignore" => true
          case _ => false
        }
      case _ => false
    }
  }

  def isSpecs2TestExpr(expr: PsiElement): Boolean = {
    expr match {
      case infixExpr: ScInfixExpr =>
        checkScInfixExpr(infixExpr, "$greater$greater", None) ||
          checkScInfixExpr(infixExpr, "$bang", None) ||
          checkScInfixExpr(infixExpr, "in", None)
      case _ => false
    }
  }

  def isSpecs2ScopeExpr(expr: PsiElement): Boolean = {
    expr match {
      case infixExpr: ScInfixExpr =>
        checkScInfixExpr(infixExpr, "should", List("org.specs2.specification.Fragment")) ||
          checkScInfixExpr(infixExpr, "should", List("org.specs2.specification.core.Fragment")) ||
          checkScInfixExpr(infixExpr, "should", List("org.specs2.specification.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
          checkScInfixExpr(infixExpr, "should", List("org.specs2.specification.core.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
          checkScInfixExpr(infixExpr, "should", List("void"), List("org.specs2.control.ImplicitParameters.ImplicitParam1",
            "org.specs2.control.ImplicitParameters.ImplicitParam2")) ||
          checkScInfixExpr(infixExpr, "can", List("org.specs2.specification.Fragment")) ||
          checkScInfixExpr(infixExpr, "can", List("org.specs2.specification.core.Fragment")) ||
          checkScInfixExpr(infixExpr, "can", List("org.specs2.specification.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
          checkScInfixExpr(infixExpr, "can", List("org.specs2.specification.core.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
          checkScInfixExpr(infixExpr, "can", List("void"), List("org.specs2.control.ImplicitParameters.ImplicitParam1",
            "org.specs2.control.ImplicitParameters.ImplicitParam2"))
      case _ => false
    }
  }

  def isSpecs2Expr(expr: PsiElement): Boolean = isSpecs2TestExpr(expr) || isSpecs2ScopeExpr(expr)

  private def extractSpecs2ScInfixExpr(expr: ScInfixExpr, children: => Array[TreeElement]): Option[Test] = {
    //check matchers here because they are supposed to be stacked, as opposed to scalaTest, where bases are distinct
    if (isSpecs2Expr(expr: ScInfixExpr)) {
      val status = if (checkSpecsPending(expr)) Test.PendingStatusId else Test.NormalStatusId
      val node = new Test(expr, getInfixExprTestName(expr), children, status)
      Some(node)
    }
    else None
  }

  private def extractScMethodCall(expr: ScMethodCall, entry: ExtractEntry): Option[Test] = {
    if (entry.canIgnore && (checkScMethodCall(expr, "ignore", scMethodCallDefaultArg: _*) ||
      checkScMethodCall(expr, "ignore", scMethodCallDefaultArgScalaTest3_v1: _*) ||
      checkScMethodCall(expr, "ignore", scMethodCallDefaultArgScalaTest3_v2: _*)))
    {
      Some(ignoredScalaTestElement(expr, getMethodCallTestName(expr), entry.children(())))
    }
    else if (entry.canPend && checkMethodCallPending(expr)) {
      Some(pendingScalaTestElement(expr, getMethodCallTestName(expr), entry.children(())))
    }
    else if (checkScMethodCall(expr, entry.funName, entry.args: _*) ||
      checkScMethodCallApply(expr, entry.funName, scMethodCallDefaultArg:_*) ||
      checkScMethodCallApply(expr, entry.funName, scMethodCallDefaultArgScalaTest3_v1:_*) ||
      checkScMethodCallApply(expr, entry.funName, scMethodCallDefaultArgScalaTest3_v2:_*)
    ) {
      Some(new Test(expr, getMethodCallTestName(expr), entry.children(())))
    }
    else None
  }

  //-----------------Here are checks for concrete test class bases---------------------
  //-----ScalaTest------
  private def extractFreeSpec(expr: ScInfixExpr, project: Project): Option[Test] = {
    lazy val children = processChildren(getInnerInfixExprs(expr), extractFreeSpec, project)
    extractScalaTestScInfixExpr(expr, ExtractEntry("$minus", true, false, _ => children, List("void"))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("void")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("java.lang.Object"))))
  }

  private def extractFlatSpec(expr: ScInfixExpr): Option[Test] = {
    extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("void"))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("java.lang.Object"))))
  }

  private def extractWordSpec(expr: ScInfixExpr, project: Project): Option[Test] = {
    lazy val children = processChildren(getInnerInfixExprs(expr), extractWordSpec, project)
    extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("void"))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("java.lang.Object")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("should", false, false, _ => children, List("void"),
        List("org.scalatest.words.StringVerbBlockRegistration")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("should", false, false, _ => children, List("void")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("must", false, false, _ => children, List("void"),
        List("org.scalatest.words.StringVerbBlockRegistration")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("must", false, false, _ => children, List("void")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("can", false, false, _ => children, List("void"),
        List("org.scalatest.words.StringVerbBlockRegistration")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("can", false, false, _ => children, List("void")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("when", false, false, _ => children, List("void")))).
      orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("which", false, false, _ => children, List("void"))))
  }

  private def extractFunSpec(expr: ScMethodCall, project: Project): Option[Test] = {
    lazy val children = processChildren(getInnerMethodCalls(expr), extractFunSpec, project)
    extractScMethodCall(expr, ExtractEntry("describe", true, true, _ => children, List("java.lang.String"), List("void"))).
      orElse(extractScMethodCall(expr, ExtractEntry("it", true, true, scMethodCallDefaultArg:_*))).
      orElse(extractScMethodCall(expr, ExtractEntry("it", true, true, scMethodCallDefaultArgScalaTest3_v1:_*))).
      orElse(extractScMethodCall(expr, ExtractEntry("it", true, true, scMethodCallDefaultArgScalaTest3_v2:_*))).
      orElse(extractScMethodCall(expr, ExtractEntry("they", true, true, scMethodCallDefaultArg:_*)))
  }

  private def extractFeatureSpec(expr: ScMethodCall, project: Project): Option[Test] = {
    lazy val children = processChildren(getInnerMethodCalls(expr), extractFeatureSpec, project)
    val entries = Seq(
      ExtractEntry("feature", true, false, _ => children, List("java.lang.String"), List("void")),
      ExtractEntry("scenario", true, true, scMethodCallDefaultArg: _*),
      ExtractEntry("scenario", true, true, scMethodCallDefaultArgScalaTest3_v1: _*),
      ExtractEntry("scenario", true, true, scMethodCallDefaultArgScalaTest3_v2: _*),
      //scalatest 3.1.0
      ExtractEntry("Feature", true, false, _ => children, List("java.lang.String"), List("void")),
      ExtractEntry("Scenario", true, true, scMethodCallDefaultArg: _*),
      ExtractEntry("Scenario", true, true, scMethodCallDefaultArgScalaTest3_v1: _*),
      ExtractEntry("Scenario", true, true, scMethodCallDefaultArgScalaTest3_v2: _*),
    )
    entries.iterator.flatMap(extractScMethodCall(expr, _)).nextOption()
  }

  private def extractPropSpec(expr: ScMethodCall): Option[Test] = {
    extractScMethodCall(expr, ExtractEntry("property", true, true, scMethodCallDefaultArg: _*)).
      orElse(extractScMethodCall(expr, ExtractEntry("property", true, true, scMethodCallDefaultArgScalaTest3_v1: _*))).
      orElse(extractScMethodCall(expr, ExtractEntry("property", true, true, scMethodCallDefaultArgScalaTest3_v2: _*)))
  }

  private def extractFunSuite(expr: ScMethodCall): Option[Test] = {
    extractScMethodCall(expr, ExtractEntry("test", true, true, scMethodCallDefaultArg:_*))
      .orElse(extractScMethodCall(expr, ExtractEntry("test", true, true, scMethodCallDefaultArgScalaTest3_v1:_*)))
      .orElse(extractScMethodCall(expr, ExtractEntry("test", true, true, scMethodCallDefaultArgScalaTest3_v2:_*)))
  }

  private val munitTestMethodCallArg = Seq(List("java.lang.String"), List("java.lang.Object"))
  private val munitPropertyMethodCallArg = Seq(List("java.lang.String"), List("org.scalacheck.Prop"))
  private val munitFunSuiteExtractEntries = Seq(
    ExtractEntry("test", false, false, munitTestMethodCallArg: _*),
    ExtractEntry("property", false, false, munitPropertyMethodCallArg: _*),
  )
  private def extractMUnitFunSuite(expr: ScMethodCall): Option[Test] = {
    val foundEntry = munitFunSuiteExtractEntries.find(entry => checkScMethodCall(expr, entry.funName, entry.args: _*))
    foundEntry.flatMap { entry =>
      Some(new Test(expr, getMethodCallTestName(expr), entry.children(())))
    }
  }

  //-----Specs2-----
  //  private def extractAcceptanceSpecification(isExpr: ScFunctionDefinition, body: ScTemplateBody,
  //                                             project: Project): Option[TestStructureViewElement] = {
  //    if (isExpr.getName == "is" && isExpr.getParameterList.getParametersCount == 0) {
  //      //ScalaElementTypes.FUNCTION_DEFINITION
  //      val children = isExpr.findChildrenByType(ScalaElementTypes.REFERENCE_EXPRESSION).filter(_.isInstanceOf[ScReferenceExpression]).
  //          map(_.asInstanceOf[ScReferenceExpression]).map(test =>(test, test.resolve())).
  //          filter{case (test, testDef) => testDef != null && testDef.isInstanceOf[ScFunctionDefinition]}.
  //          map{case (test, testDef) => new TestStructureViewElement(testDef, testDef.asInstanceOf[ScFunctionDefinition].getName)}
  //      Some(new TestStructureViewElement(isExpr, isExpr.getName, children.toArray))
  //    } else None
  //  }

  private def extractUnitSpec(expr: ScInfixExpr, project: Project): Option[Test] = {
    lazy val children = processChildren(getInnerInfixExprs(expr), extractUnitSpec, project)
    extractSpecs2ScInfixExpr(expr, children)
  }

  //-----uTest-----
  private def extractUTest(expr: ScMethodCall, project: Project): ju.Collection[TreeElement] = {
    def extractUTestInner(expr: PsiElement, project: Project): Option[TreeElement] = {
      if (isUTestInfixExpr(expr)) {
        Some(new Test(expr, getInfixExprTestName(expr.asInstanceOf[ScInfixExpr]),
          processChildren(getInnerExprs(expr), extractUTestInner, project)))
      } else if (isUTestApplyCall(expr)) {
        Some(new Test(expr, getMethodCallTestName(expr.asInstanceOf[ScMethodCall]),
          processChildren(getInnerExprs(expr), extractUTestInner, project)))
      } else None
    }
    if (isUTestSuiteApplyCall(expr) || isUTestTestsCall(expr)) {
      import scala.jdk.CollectionConverters._
      expr.args.findFirstChildByType(ScCodeBlockElementType.BlockExpression) match {
        case Some(blockExpr: ScBlockExpr) =>
          (
            for {
              methodExpr <- blockExpr.children
              if methodExpr.is[ScInfixExpr, ScMethodCall]
              inner <- extractUTestInner(methodExpr, project)
            } yield inner
          ).toList.asJava
        case _ => new ju.ArrayList[TreeElement]
      }
    } else new ju.ArrayList[TreeElement]()
  }

  def isUTestInfixExpr(psiElement: PsiElement): Boolean = psiElement match {
    case inifxExpr: ScInfixExpr => checkScInfixExpr(inifxExpr, "$minus", List("java.lang.Object")) ||
      checkScInfixExpr(inifxExpr, "$minus", List())
    case _ => false
  }

  // `testName - {}
  def isUTestApplyCall(psiElement: PsiElement): Boolean = psiElement match {
    case methodCall: ScMethodCall =>
      methodCall.getEffectiveInvokedExpr match {
        case literal: ScLiteral =>
          literal.implicitElement()
            .collect { case definition: ScFunctionDefinition => definition }
            .filter(definition => definition.getName == "TestableSymbol" && definition.isSynthetic)
            .exists(definition => checkClauses(definition.getParameterList.clauses, List(List("scala.Symbol"))))
        case _ => false
      }
    case _ => false
  }

  // test("testName") {}
  def isUTestObjectApplyCall(psiElement: PsiElement): Boolean = psiElement match {
    case methodCall: ScMethodCall =>
      methodCall.getEffectiveInvokedExpr match {
        case methodCall2: ScMethodCall =>
          methodCall2.getEffectiveInvokedExpr match {
            case ScReferenceExpression(d@ScFunctionDefinition.withName("apply") /*&& Parent(???))*/)  =>
              d.containingClass.qualifiedName == "utest.test"
            case _ => false
          }
        case _ => false
      }
    case _ => false
  }

  def isUTestTestsCall(psiElement: PsiElement): Boolean = psiElement match {
    case methodCall: ScMethodCall => checkScMethodCallApply(methodCall, "Tests", List("void"))
    case _ => false
  }

  def isUTestSuiteApplyCall(psiElement: PsiElement): Boolean = psiElement match {
    case methodCall: ScMethodCall => checkScMethodCallApply(methodCall, "TestSuite", List("void"))
    case _ => false
  }

  private def findListOfPatternsWithIndex(elem: PsiElement): Option[(ScPatternList, Option[(Int, Int)])] = {
    def checkParent[T](currentElement: PsiElement, parentType: Class[T]): Option[T] = {
      currentElement.getParent match {
        case parent if parentType.isInstance(parent) =>
          Some(parent.asInstanceOf[T])
        case _ => None
      }
    }

    checkParent(elem, classOf[ScReferencePattern]).flatMap(checkParent(_, classOf[ScPatternsImpl])).
      flatMap(checkParent(_, classOf[ScTuplePattern])).flatMap(checkParent(_, classOf[ScPatternList])) match {
      case Some(patternList) =>
        val refPattern = elem.getParent
        val patternsImpl = refPattern.getParent.asInstanceOf[ScPatternsImpl]
        val index = patternsImpl.patterns.zipWithIndex.
          find{case (pat, _) => pat == refPattern}.get._2
        Some((patternList, Some(index, patternsImpl.patterns.size)))
      case _ => checkParent(elem, classOf[ScReferencePattern]).flatMap(checkParent(_, classOf[ScPatternList])).orElse(
        elem.getParent match {
          case parent: ScPatternDefinition if parent.bindings.size == 1 =>
            Option(PsiTreeUtil.getChildOfType(parent, classOf[ScPatternList]))
          case _ => None
        }
      ).map((_, None))
    }
  }

  def getUTestLeftHandTestDefinition(element: PsiElement): Option[ScMethodCall] = findListOfPatternsWithIndex(element) match {
    case Some((pattern, indexOpt)) if pattern.getParent != null && pattern.getParent.isInstanceOf[ScPatternDefinition] =>
      ((pattern.getParent.getLastChild, indexOpt) match {
        case (suiteMethodCall: ScMethodCall, None) => suiteMethodCall //left-hand is a simple pattern
        case (tuple: ScTuple, Some((index, size))) if size == tuple.exprs.size =>
          //left-hand is a tuple
          tuple.exprs(index)
        case _ => null
      }) match {
        case suite: ScMethodCall if TestNodeProvider.isUTestSuiteApplyCall(suite) ||
          TestNodeProvider.isUTestTestsCall(suite) => Some(suite) //getTestSuiteName(suite).orNull
        case _ => None
      }
    case _ => None
  }

  def getTestNames(aSuite: ScTypeDefinition, configurationProducer: AbstractTestConfigurationProducer[_]): Seq[String] = {
    @tailrec
    def getTestLeaves(elements: Iterable[TreeElement], res: List[Test] = List()): List[Test] = {
      if (elements.isEmpty) res else {
        val head = elements.head
        (head, head.getChildren) match {
          case (testHead: Test, e) if e.isEmpty => getTestLeaves(elements.tail, testHead :: res)
          case (_, children) => getTestLeaves(children ++ elements.tail, res)
        }
      }
    }
    val suiteName = aSuite.getQualifiedName

    val nodeProvider = new TestNodeProvider

    val isUTest = configurationProducer.isInstanceOf[UTestConfigurationProducer]
    val elements: Iterable[TreeElement] =
      if (isUTest) {
        val children = new TypeDefinition(aSuite).getChildren
        children.flatMap {
          case scVal: Value if !scVal.inherited => nodeProvider.provideNodes(scVal).asScala
          case _                                => List.empty
        }
      }
      else
        nodeProvider.provideNodes(new TypeDefinition(aSuite)).asScala

    val leaves = getTestLeaves(elements)
    val suitesWithTestNames = leaves.flatMap { e =>
      configurationProducer.getTestClassWithTestName(new PsiLocation(e.element))
    }

    suitesWithTestNames.collect {
      case ClassWithTestName(suite, Some(testName)) if suite.getQualifiedName == suiteName =>
        testName
    }
  }
}

case class ExtractEntry(funName: String, canIgnore: Boolean, canPend: Boolean, children: Unit => Array[TreeElement], args: List[String]*)

object ExtractEntry {
  def apply(funName: String, canIgnore: Boolean, canPend: Boolean, args: List[String]*): ExtractEntry =
    ExtractEntry(funName, canIgnore, canPend, _ => Array[TreeElement](), args: _*)
}
