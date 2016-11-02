package org.jetbrains.plugins.scala.testingSupport.test.structureView

import java.util

import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.project.{IndexNotReadyException, Project}
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScReferencePattern, ScTuplePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScPatternsImpl
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
        val clazz = td.psiElement
        val project = clazz.getProject
        try {
          if (!clazz.isValid) return children
          clazz.extendsBlock.templateBody match {
            case Some(body) =>
              for (expr <- body.exprs) {
                (expr match {
                  case expr: ScMethodCall =>
                    TestNodeProvider.extractTestViewElement(expr, clazz, project)
                  case expr: ScInfixExpr =>
                    TestNodeProvider.extractTestViewElementInfix(expr, clazz, project)
                  case expr: ScPatternDefinition =>
                    TestNodeProvider.extractTestViewElementPatternDef(expr, clazz, project)
                  case _ =>
                    None
                }).map(children.add)
              }
            case _ =>
          }
          children
        }
        catch {
          case _: IndexNotReadyException => new util.ArrayList[TreeElement]()
        }
      case valElement: ScalaValueStructureViewElement =>
        def tryTupledId(psiElement: PsiElement) = TestNodeProvider.getUTestLeftHandTestDefinition(psiElement) match {
          case Some(testTupleDefinition) => TestNodeProvider.extractUTest(testTupleDefinition, testTupleDefinition.getProject)
          case _ => new util.ArrayList[TreeElement]()
        }

        valElement.getValue match {
          case valDef: ScPatternDefinition =>
            valDef.getLastChild match {
              case testCall: ScMethodCall =>
                TestNodeProvider.extractUTest(testCall, testCall.getProject)
              case _ => tryTupledId(valElement.psiElement)
            }
          case named: ScNamedElement =>
            tryTupledId(named.nameId)
          case _ => new util.ArrayList[TreeElement]()
        }
      case _ => new util.ArrayList[TreeElement]()
    }
  }
}

object TestNodeProvider {
  val ignoredSuffix = " !!! IGNORED !!!"
  val pendingSuffix = " (pending)"

  private def getInnerInfixExprs(expr: ScInfixExpr) = {
    expr.getLastChild.getChildren.filter(_.isInstanceOf[ScInfixExpr]).map(_.asInstanceOf[ScInfixExpr])
  }

  private def extractTestViewElementInfix(expr: ScInfixExpr, clazz: ScTypeDefinition, project: Project):
  Option[TestStructureViewElement] = {
    import ScalaTestUtil._
    import Specs2Util._
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (flatSpecBases.exists(isInheritor(clazz, _))) {
      extractFlatSpec(expr, project)
    } else if (freeSpecBases.exists(isInheritor(clazz, _))) {
      extractFreeSpec(expr, project)
    } else if (wordSpecBases.exists(isInheritor(clazz, _))) {
      extractWordSpec(expr, project)
    } else if (isInheritor(clazz, unitSpecBase)) {
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

  private def extractTestViewElement(expr: ScMethodCall, clazz: ScTypeDefinition, project: Project):
  Option[TestStructureViewElement] = {
    import ScalaTestUtil._
    import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
    if (funSuiteBases.exists(isInheritor(clazz, _))) {
      extractFunSuite(expr, project)
      //this should be a funSuite-like test
    } else if (featureSpecBases.exists(isInheritor(clazz, _))) {
      extractFeatureSpec(expr, project)
    } else if (funSpecBasesPost2_0.exists(isInheritor(clazz, _)) || funSpecBasesPre2_0.exists(isInheritor
      (clazz, _))) {
      extractFunSpec(expr, project)
    } else if (propSpecBases.exists(isInheritor(clazz, _))) {
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
    new TestStructureViewElement(element, name + TestNodeProvider.ignoredSuffix, children, TestStructureViewElement.ignoredStatusId)

  private def pendingScalaTestElement(element: PsiElement, name: String, children: Array[TreeElement] = Array()) =
    new TestStructureViewElement(element, name + TestNodeProvider.pendingSuffix, children, TestStructureViewElement.pendingStatusId)

  def getInfixExprTestName(expr: ScInfixExpr): String = expr.getNode.getFirstChildNode.getText

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
          resolveResult.getActualElement.name == callerName && {
            val funElement = resolveResult.innerResolveResult match {
              case Some(innerResult) =>
                innerResult.getActualElement
              case None => resolveResult.getElement
            }
            funElement.name == "apply" && funElement.isInstanceOf[ScFunctionDefinition] &&
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
        paramNames.forall(checkClauses(funDef.getParameterList.clauses, _: _*))})
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
      Some(ignoredScalaTestElement(expr, getInfixExprTestName(expr), entry.children(())))
    } else if (checkScInfixExpr(expr, "is", List("org.scalatest.PendingNothing")) || checkPendingInfixExpr(expr)) {
      Some(pendingScalaTestElement(expr, getInfixExprTestName(expr), entry.children(())))
    } else if (checkScInfixExpr(expr, entry.funName, entry.args: _*)) {
      Some(new TestStructureViewElement(expr, getInfixExprTestName(expr), entry.children(())))
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

  private def extractSpecs2ScInfixExpr(expr: ScInfixExpr, children: => Array[TreeElement], project: Project): Option[TestStructureViewElement] = {
    //check matchers here because they are supposed to be stacked, as opposed to scalaTest, where bases are distinct
    if (isSpecs2Expr(expr: ScInfixExpr)) {
      Some(new TestStructureViewElement(expr, getInfixExprTestName(expr), children,
        if (checkSpecsPending(expr)) TestStructureViewElement.pendingStatusId else TestStructureViewElement.normalStatusId))
    }  else None
  }

  private def extractScMethodCall(expr: ScMethodCall, entry: ExtractEntry, project: Project):
  Option[TestStructureViewElement] = {
    if (entry.canIgnore && checkScMethodCall(expr, "ignore", scMethodCallDefaultArg: _*)) {
      Some(ignoredScalaTestElement(expr, getMethodCallTestName(expr), entry.children(())))
    } else if (entry.canPend && checkMethodCallPending(expr)) {
      Some(pendingScalaTestElement(expr, getMethodCallTestName(expr), entry.children(())))
    } else if (checkScMethodCall(expr, entry.funName, entry.args: _*) || checkScMethodCallApply(expr, entry.funName, scMethodCallDefaultArg:_*)) {
      Some(new TestStructureViewElement(expr, getMethodCallTestName(expr), entry.children(())))
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
    def extractUTestInner(expr: PsiElement, project: Project): Option[TestStructureViewElement] = {
      if (isUTestInfixExpr(expr)) {
        Some(new TestStructureViewElement(expr, getInfixExprTestName(expr.asInstanceOf[ScInfixExpr]),
          processChildren(getInnerExprs(expr), extractUTestInner, project)))
      } else if (isUTestApplyCall(expr)) {
        Some(new TestStructureViewElement(expr, getMethodCallTestName(expr.asInstanceOf[ScMethodCall]),
          processChildren(getInnerExprs(expr), extractUTestInner, project)))
      } else None
    }
    if (isUTestSuiteApplyCall(expr)) {
      import scala.collection.JavaConversions._
      expr.args.findFirstChildByType(ScalaElementTypes.BLOCK_EXPR) match {
        case blockExpr: ScBlockExpr => (for (methodExpr <- blockExpr.children if methodExpr.isInstanceOf[ScInfixExpr] || methodExpr.isInstanceOf[ScMethodCall])
          yield extractUTestInner(methodExpr, project)).filter(_.isDefined).map(_.get).toList
        case _ => new util.ArrayList[TreeElement]
      }
    } else new util.ArrayList[TreeElement]()
  }

  def isUTestInfixExpr(psiElement: PsiElement): Boolean = psiElement match {
    case inifxExpr: ScInfixExpr => checkScInfixExpr(inifxExpr, "$minus", List("java.lang.Object")) ||
      checkScInfixExpr(inifxExpr, "$minus", List())
    case _ => false
  }

  def isUTestApplyCall(psiElement: PsiElement): Boolean = psiElement match {
    case methodCall: ScMethodCall =>
      val literal = methodCall.getEffectiveInvokedExpr
      literal.isInstanceOf[ScLiteral] && {
        val (_, actualType, _, _) = literal.getImplicitConversions()
        actualType match {
          case Some(funDef: ScFunctionDefinition) =>
            funDef.getName == "TestableSymbol" && funDef.isSynthetic && checkClauses(funDef.getParameterList.clauses, List("scala.Symbol"))
          case _ => false
        }
      }
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
        case suite: ScMethodCall if TestNodeProvider.isUTestSuiteApplyCall(suite) => Some(suite) //getTestSuiteName(suite).orNull
        case _ => None
      }
    case _ => None
  }
}

case class ExtractEntry(funName: String, canIgnore: Boolean, canPend: Boolean, children: (Unit => Array[TreeElement]), args: List[String]*) {
}

object ExtractEntry {
  def apply(funName: String, canIgnore: Boolean, canPend: Boolean, args: List[String]*): ExtractEntry =
    ExtractEntry(funName, canIgnore, canPend, _ => Array[TreeElement](), args: _*)
}
