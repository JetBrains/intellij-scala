package org.jetbrains.plugins.scala.testingSupport.test.structureView

import java.util

import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.project.{Project, IndexNotReadyException}
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.structureView.elements.impl._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestUtil
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2Util

/**
 * @author Roman.Shein
 * @since 10.04.2015.
 */
class TestNodeProvider extends FileStructureNodeProvider[TreeElement] {
  override def getCheckBoxText: String = "Show scala tests" //TODO: get text from bundle

  override def getShortcut: Array[Shortcut] = Array[Shortcut]()

  //TODO: what icon should be used here?
  override def getPresentation: ActionPresentation = new ActionPresentationData(
    getCheckBoxText, null, IconLoader.getIcon("/hierarchy/supertypes.png"))

  override def getName: String = "SCALA_SHOW_SCALATEST_TESTS"

  override def provideNodes(node: TreeElement): util.Collection[TreeElement] = {
    node match {
      case td: ScalaTypeDefinitionStructureViewElement =>
        val children = new util.ArrayList[TreeElement]()
        val clazz = td.element
        val project = clazz.getProject
        try {
          if (!clazz.isValid) return children
          clazz.extendsBlock.templateBody match {
            case Some(body) =>
              for (expr <- body.exprs) {
                (expr match {
                  case expr: ScMethodCall =>
                    extractTestViewElement(expr, clazz, project)
                  case expr: ScInfixExpr =>
                    extractTestViewElementInfix(expr, clazz, project)
                  case expr: ScPatternDefinition =>
                    extractTestViewElementPatternDef(expr, clazz, project)
                  case _ =>
                    None
                }).map(children.add)
              }
            case _ =>
          }
          children
        }
        catch {
          case e: IndexNotReadyException => new util.ArrayList[TreeElement]()
        }
      case valElement: ScalaValueStructureViewElement =>
        valElement.getValue match {
          case valDef: ScPatternDefinition =>
            valDef.getLastChild match {
              case testCall: ScMethodCall =>
                extractUTest(testCall, testCall.getProject)
              case _ =>
                new util.ArrayList[TreeElement]()
            }
          case _ => new util.ArrayList[TreeElement]()
        }
      case _ => new util.ArrayList[TreeElement]()
    }
  }

  private def getInnerInfixExprs(expr: ScInfixExpr) = {
    expr.getLastChild.getChildren.filter(_.isInstanceOf[ScInfixExpr]).map(_.asInstanceOf[ScInfixExpr])
  }

  private def extractTestViewElementInfix(expr: ScInfixExpr, clazz: ScTypeDefinition, project: Project):
  Option[TestStructureViewElement] = {
    import ScalaTestUtil._
    import Specs2Util._
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (getFlatSpecBases.exists(isInheritor(clazz, _))) {
      extractFlatSpec(expr, project)
    } else if (getFreeSpecBases.exists(isInheritor(clazz, _))) {
      extractFreeSpec(expr, project)
    } else if (getWordSpecBases.exists(isInheritor(clazz, _))) {
      extractWordSpec(expr, project)
    } else if (isInheritor(clazz, getUnitSpecBase)) {
      extractUnitSpec(expr, project)
    } else None
  }

  private def extractTestViewElementPatternDef(pDef: ScPatternDefinition, clazz: ScTypeDefinition, project: Project):
  Option[TestStructureViewElement] = {
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (isInheritor(clazz, "utest.framework.TestSuite") && pDef.getLastChild.isInstanceOf[ScMethodCall]) {
      val methodCall = pDef.getLastChild.asInstanceOf[ScMethodCall]
      checkScMethodCall(methodCall, "apply")
      None
    } else None
  }

//  private def extractTestViewFunDef(expr: ScFunctionDefinition, body: ScTemplateBody, clazz: ScTypeDefinition, project: Project):
//  Option[TestStructureViewElement] = {
//    import Specs2Util._
//    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
//    if (isInheritor(clazz, getAcceptanseSpecBase)) {
//      extractAcceptanceSpecification(expr, body, project)
//    } else None
//  }

  private def extractTestViewElement(expr: ScMethodCall, clazz: ScTypeDefinition, project: Project):
  Option[TestStructureViewElement] = {
    import ScalaTestUtil._
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (getFunSuiteBases.exists(isInheritor(clazz, _))) {
      extractFunSuite(expr, project)
      //this should be a funSuite-like test
    } else if (getFeatureSpecBases.exists(isInheritor(clazz, _))) {
      extractFeatureSpec(expr, project)
    } else if (getFunSpecBasesPost2_0.exists(isInheritor(clazz, _)) || getFunSpecBasesPre2_0.exists(isInheritor
        (clazz, _))) {
      extractFunSpec(expr, project)
    } else if (getPropSpecBases.exists(isInheritor(clazz, _))) {
      extractPropSpec(expr, project)
    } else None
  }

  private val scMethodCallDefaultArg = Seq(List("java.lang.String", "scala.collection.Seq<org.scalatest.Tag>"), List
      ("void"))

  private def getMethodCallTestName(expr: ScMethodCall) =
  //TODO: this is horrible
    expr.getNode.getFirstChildNode.getText

  private def processChildren[T <: PsiElement](children: Array[T],
                                               processFun: (T, Project) => Option[TestStructureViewElement],
                                               project: Project): Array[TreeElement] = {
    children.map(element => processFun(element, project)).filter(_.isDefined).map(_.get)
  }

  private def getInnerMethodCalls(expr: ScMethodCall) = {
    expr.args.getLastChild.getChildren.filter(_.isInstanceOf[ScMethodCall]).map(_.asInstanceOf[ScMethodCall])
  }

//  private def maybePendingMethodCall(expr: ScMethodCall): TestStructureViewElement = {
//    if (checkMethodCallPending(expr)) pendingScalaTestElement(expr, getMethodCallTestName(expr))
//    else new TestStructureViewElement(expr, getMethodCallTestName(expr))
//  }

  private def checkMethodCallPending(expr: ScMethodCall): Boolean = {
    expr.getLastChild match {
      case args: ScArgumentExprList =>
        val lastChild = args.getChildren.apply(0)
        args.getChildren.length == 1 && lastChild.isInstanceOf[ScReferenceExpression] &&
            checkRefExpr(lastChild.asInstanceOf[ScReferenceExpression], "pending")
      case _ => false
    }
  }

  private def ignoredScalaTestElement(element: PsiElement, name: String, children: Array[TreeElement] = Array()) =
    new TestStructureViewElement(element, name + TestNodeProvider.ignoredSuffix, children, TestStructureViewElement.ignoredStatusId)

  private def pendingScalaTestElement(element: PsiElement, name: String, children: Array[TreeElement] = Array()) =
    new TestStructureViewElement(element, name + TestNodeProvider.pendingSuffix, children, TestStructureViewElement.pendingStatusId)

  private def getInfixExprTestName(expr: ScInfixExpr) = expr.getNode.getFirstChildNode.getText

  private def checkScMethodCall(expr: ScMethodCall, funName: String, paramNames: List[String]*): Boolean = {
    val methodExpr = expr.getEffectiveInvokedExpr.findFirstChildByType(ScalaElementTypes.REFERENCE_EXPRESSION)
    methodExpr != null && checkRefExpr(methodExpr.asInstanceOf[ScReferenceExpression], funName, paramNames: _*)
  }

  private def checkScMethodCallApply(expr: ScMethodCall, callerName: String, paramNames: List[String]*): Boolean = {
    val methodExpr = expr.getEffectiveInvokedExpr match {
      case refExpr: ScReferenceExpression => refExpr
      case otherExpr => otherExpr.findFirstChildByType(ScalaElementTypes.REFERENCE_EXPRESSION)
    }
    methodExpr != null && {
      methodExpr.asInstanceOf[ScReferenceExpression].advancedResolve match {
        case Some(resolveResult) =>
          resolveResult.getActualElement.getName == callerName && {
            val funElement = resolveResult.innerResolveResult match {
            case Some(innerResult) =>
              innerResult.getActualElement
            case None => resolveResult.getElement
            }
            funElement.getName == "apply" && funElement.isInstanceOf[ScFunctionDefinition] &&
                checkClauses(funElement.asInstanceOf[ScFunctionDefinition].getParameterList.clauses, paramNames: _*)
          }
        case _ => false
      }
    }
  }

  private def checkClauses(clauses: Seq[ScParameterClause], paramNames: List[String]*): Boolean = {
    clauses.length == paramNames.length && (clauses zip paramNames).forall {
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

  private def checkRefExpr(refExpr: ScReferenceExpression, funName: String, funDef: Option[ScFunctionDefinition],
                           paramNames: Option[Seq[List[String]]]): Boolean = {
    funDef.orElse(refExpr.resolve() match {
      case funDef: ScFunctionDefinition => Some(funDef)
      case _ => None
    }).exists(funDef => {
      funDef.getName == funName &&
        paramNames.map(checkClauses(funDef.getParameterList.clauses, _:_*)).getOrElse(true)})
  }

  private def checkSpecsPending(expr: ScInfixExpr): Boolean = {
    (expr.getLastChild match {
      case refExpr: ScReferenceExpression =>
        Some(refExpr)
      case methodCall: ScMethodCall =>
        methodCall.getEffectiveInvokedExpr match {
          case ref: ScReferenceExpression => Some(ref)
          case otherExpr =>
            Option(otherExpr.findFirstChildByType(ScalaElementTypes.REFERENCE_EXPRESSION)).
                map(_.asInstanceOf[ScReferenceExpression])
        }
      case _ => None
    }).exists(refExpr => checkRefExpr(refExpr, "pendingUntilFixed", List("java.lang.String")) || checkRefExpr
        (refExpr, "pendingUntilFixed"))
  }

  private def extractScalaTestScInfixExpr(expr: ScInfixExpr, entry: ExtractEntry, project: Project):
  Option[TestStructureViewElement] = {
    if (entry.canIgnore && (checkScInfixExpr(expr, "ignore", List("void")) || checkIgnoreExpr(expr))) {
      Some(ignoredScalaTestElement(expr, getInfixExprTestName(expr), entry.children()))
    } else if (checkScInfixExpr(expr, "is", List("org.scalatest.PendingNothing")) || checkPendingInfixExpr(expr)) {
      Some(pendingScalaTestElement(expr, getInfixExprTestName(expr), entry.children()))
    } else if (checkScInfixExpr(expr, entry.funName, entry.args: _*)) {
      Some(new TestStructureViewElement(expr, getInfixExprTestName(expr), entry.children()))
    } else None
  }

  private def checkPendingInfixExpr(expr: ScInfixExpr): Boolean = {
    expr.getLastChild match {
      case expr: ScExpression => checkPendingExpr(expr)
      case _ => false
    }
  }

  private def checkPendingExpr(expr: ScExpression): Boolean = {
    Option(expr match {
      case refExpr: ScReferenceExpression => refExpr
      case _ => expr.findLastChildByType(ScalaElementTypes.REFERENCE_EXPRESSION)
    }).exists(checkRefExpr(_, "pending"))
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

  private def extractSpecs2ScInfixExpr(expr: ScInfixExpr, children: => Array[TreeElement], project: Project): Option[TestStructureViewElement] = {
    //check matchers here because they are supposed to be stacked, as opposed to scalaTest, where bases are distinct
    if (checkScInfixExpr(expr, "should", List("org.specs2.specification.Fragment")) ||
        checkScInfixExpr(expr, "should", List("org.specs2.specification.core.Fragment")) ||
        checkScInfixExpr(expr, "should", List("org.specs2.specification.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
        checkScInfixExpr(expr, "should", List("org.specs2.specification.core.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
        checkScInfixExpr(expr, "should", List("void"), List("org.specs2.control.ImplicitParameters.ImplicitParam1",
          "org.specs2.control.ImplicitParameters.ImplicitParam2")) ||
        checkScInfixExpr(expr, "can", List("org.specs2.specification.Fragment")) ||
        checkScInfixExpr(expr, "can", List("org.specs2.specification.core.Fragment")) ||
        checkScInfixExpr(expr, "can", List("org.specs2.specification.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
        checkScInfixExpr(expr, "can", List("org.specs2.specification.core.Fragment"), List("org.specs2.control.ImplicitParameters.ImplicitParam")) ||
        checkScInfixExpr(expr, "can", List("void"), List("org.specs2.control.ImplicitParameters.ImplicitParam1",
          "org.specs2.control.ImplicitParameters.ImplicitParam2")) ||
        checkScInfixExpr(expr, "$greater$greater", None) ||
        checkScInfixExpr(expr, "$bang", None) ||
        checkScInfixExpr(expr, "in", None)) {
      Some(new TestStructureViewElement(expr, getInfixExprTestName(expr), children,
        if (checkSpecsPending(expr)) TestStructureViewElement.pendingStatusId else TestStructureViewElement.normalStatusId))
    }  else None
  }

  private def extractScMethodCall(expr: ScMethodCall, entry: ExtractEntry, project: Project):
  Option[TestStructureViewElement] = {
    if (entry.canIgnore && checkScMethodCall(expr, "ignore", scMethodCallDefaultArg: _*)) {
      Some(ignoredScalaTestElement(expr, getMethodCallTestName(expr), entry.children()))
    } else if (entry.canPend && checkMethodCallPending(expr)) {
      Some(pendingScalaTestElement(expr, getMethodCallTestName(expr), entry.children()))
    } else if (checkScMethodCall(expr, entry.funName, entry.args: _*) || checkScMethodCallApply(expr, entry.funName, scMethodCallDefaultArg:_*)) {
      Some(new TestStructureViewElement(expr, getMethodCallTestName(expr), entry.children()))
    } else None
  }

  //-----------------Here are checks for concrete test class bases---------------------
  //-----ScalaTest------
  private def extractFreeSpec(expr: ScInfixExpr, project: Project): Option[TestStructureViewElement] = {
    lazy val children = processChildren(getInnerInfixExprs(expr), extractFreeSpec, project)
    extractScalaTestScInfixExpr(expr, ExtractEntry("$minus", true, false, _ => children, List("void")), project).
        orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("void")), project))
  }

  private def extractFlatSpec(expr: ScInfixExpr, project: Project): Option[TestStructureViewElement] = {
    extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("void")), project)
  }

  private def extractWordSpec(expr: ScInfixExpr, project: Project): Option[TestStructureViewElement] = {
    lazy val children = processChildren(getInnerInfixExprs(expr), extractWordSpec, project)
    extractScalaTestScInfixExpr(expr, ExtractEntry("in", true, true, List("void")), project).
        orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("should", false, false, _ => children, List("void"),
      List("org.scalatest.words.StringVerbBlockRegistration")), project)).
        orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("must", false, false, _ => children, List("void"),
      List("org.scalatest.words.StringVerbBlockRegistration")), project)).
        orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("can", false, false, _ => children, List("void"),
      List("org.scalatest.words.StringVerbBlockRegistration")), project)).
        orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("when", false, false, _ => children, List("void")), project)).
        orElse(extractScalaTestScInfixExpr(expr, ExtractEntry("which", false, false, _ => children, List("void")), project))
  }

  private def extractFunSpec(expr: ScMethodCall, project: Project): Option[TestStructureViewElement] = {
    lazy val children = processChildren(getInnerMethodCalls(expr), extractFunSpec, project)
    extractScMethodCall(expr, ExtractEntry("describe", true, true, _ => children, List("java.lang.String"), List("void")),
      project).orElse(extractScMethodCall(expr, ExtractEntry("it", true, true, scMethodCallDefaultArg:_*), project)).
      orElse(extractScMethodCall(expr, ExtractEntry("they", true, true, scMethodCallDefaultArg:_*), project))
  }

  private def extractFeatureSpec(expr: ScMethodCall, project: Project): Option[TestStructureViewElement] = {
    lazy val children = processChildren(getInnerMethodCalls(expr), extractFeatureSpec, project)
    extractScMethodCall(expr, ExtractEntry("feature", true, false, _ => children, List("java.lang.String"), List("void")), project).
        orElse(extractScMethodCall(expr, ExtractEntry("scenario", true, true, scMethodCallDefaultArg:_*), project))
  }

  private def extractPropSpec(expr: ScMethodCall, project: Project): Option[TestStructureViewElement] = {
    extractScMethodCall(expr, ExtractEntry("property", true, true, scMethodCallDefaultArg:_*), project)
  }

  private def extractFunSuite(expr: ScMethodCall, project: Project): Option[TestStructureViewElement] = {
    extractScMethodCall(expr, ExtractEntry("test", true, true, scMethodCallDefaultArg:_*), project)
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

  private def extractUnitSpec(expr: ScInfixExpr, project: Project): Option[TestStructureViewElement] = {
    lazy val children = processChildren(getInnerInfixExprs(expr), extractUnitSpec, project)
    extractSpecs2ScInfixExpr(expr, children, project)
  }

  //-----uTest-----
  private def extractUTest(expr: ScMethodCall, project: Project): util.Collection[TreeElement] = {
    def extractUTestInner(expr: ScInfixExpr, project: Project): Option[TestStructureViewElement] = {
      if (checkScInfixExpr(expr, "$minus", List("java.lang.Object"))) {
        Some(new TestStructureViewElement(expr, getInfixExprTestName(expr), processChildren(getInnerInfixExprs(expr), extractUTestInner, project)))
      } else None
    }
    if (checkScMethodCallApply(expr, "TestSuite$", List("void"))) {
      import scala.collection.JavaConversions._
      expr.args.findFirstChildByType(ScalaElementTypes.BLOCK_EXPR) match {
        case blockExpr: ScBlockExpr => (for (infixExpr <- blockExpr.children if infixExpr.isInstanceOf[ScInfixExpr])
          yield extractUTestInner(infixExpr.asInstanceOf[ScInfixExpr], project)).filter(_.isDefined).map(_.get).toList
        case _ => new util.ArrayList[TreeElement]
      }
    } else new util.ArrayList[TreeElement]()
  }
}

object TestNodeProvider {
  val ignoredSuffix = " !!! IGNORED !!!"
  val pendingSuffix = " (pending)"
}

case class ExtractEntry(funName: String, canIgnore: Boolean, canPend: Boolean, children: (Unit => Array[TreeElement]), args: List[String]*) {
}

object ExtractEntry {
  def apply(funName: String, canIgnore: Boolean, canPend: Boolean, args: List[String]*): ExtractEntry =
    ExtractEntry(funName, canIgnore, canPend, _ => Array[TreeElement](), args: _*)
}