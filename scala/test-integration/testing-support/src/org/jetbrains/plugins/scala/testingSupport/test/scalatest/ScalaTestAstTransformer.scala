package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import com.intellij.execution.Location
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil._
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestUtil.{itWordFqns, theyWordFqns}
import org.jetbrains.plugins.scala.testingSupport.test.utils.StringOps
import org.scalatest.finders.{MethodInvocation => _, _}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object ScalaTestAstTransformer {

  private val LOG: Logger = Logger.getInstance(ScalaTestAstTransformer.getClass)
  private val FindersAnnotationFqn = "org.scalatest.Finders"

  def testSelection(location: Location[_ <: PsiElement]): Option[Selection] = {
    val element = location.getPsiElement
    val typeDef = PsiTreeUtil.getNonStrictParentOfType(element, classOf[ScClass], classOf[ScTrait])

    if (typeDef == null) return None
    Try(testSelection(element, typeDef)) match {
      case Failure(e)     =>
        LOG.debug(s"Failed to load scalatest-finders API class for test suite ${typeDef.qualifiedName}", e)
        None
      case Success(value) =>
        value
    }
  }

  private def testSelection(element: PsiElement, typeDef: ScTypeDefinition): Option[Selection] =
    for {
      finder    <- getFinder(typeDef)
      selected  <- getSelectedAstNode(typeDef.qualifiedName, element)
      selection <- Option(finder.find(selected))
    } yield selection

  def getFinder(clazz: ScTypeDefinition): Option[Finder] = {
    val classes = MixinNodes.linearization(clazz).flatMap(_.extractClass.toSeq)

    for (clazz <- classes) {
      clazz match {
        case td: ScTypeDefinition =>
          ProgressManager.checkCanceled()

          val finderFqnOpt = getFinderClassFqn(td)
          finderFqnOpt match {
            case Some(finderFqn) =>
              try {
                val finderClass: Class[_] = Class.forName(finderFqn)
                return Option(finderClass.getDeclaredConstructor().newInstance().asInstanceOf[Finder])
              } catch {
                case _: ClassNotFoundException =>
                  LOG.debug(s"Failed to load finders API class $finderFqn")
              }
            case _ =>
          }
        case _ =>
      }
    }

    None
  }

  private def getSelectedAstNode(className: String, element: PsiElement): Option[AstNode] = {
    val withParentsNodes = element.withParents.flatMap(transformNode(className, _))
    val astNode = withParentsNodes.nextOption()
    astNode
  }

  private def getNameFromAnnotLiteral(expr: ScExpression): Option[String] = expr match {
    case lit: ScLiteral if lit.isString => Some(lit.getValue.toString)
    case _ => None
  }

  private def getNameFromAnnotAssign(assignStmt: ScAssignment): Option[String] = {
    assignStmt.leftExpression match {
      case expression: ScReferenceExpression if expression.refName == "value" =>
        val expr = assignStmt.rightExpression.get
        if (expr != null) {
          val exprAdjusted = expr match {
            case methodCall: ScMethodCall =>
              getExpressionFromAnnotCall(methodCall).getOrElse(expr)
            case _ =>
              expr
          }
          getNameFromAnnotLiteral(exprAdjusted)
        }
        else None
      case _ => None
    }
  }

  private def getExpressionFromAnnotCall(methodCall: ScMethodCall): Option[ScExpression] =
    methodCall.getInvokedExpr match {
      // TODO Resolve reference, check fully-qualified name, apply method, SCL-21533
      case ref: ScReferenceExpression if ref.refName == "Array" =>
        val constructorArgs = methodCall.args
        constructorArgs.exprs match {
          // TODO Support multiple arguments, SCL-21533
          // TODO There's an optional second argument list, SCL-21533
          case Seq(single) if constructorArgs.invocationCount == 1 =>
            Some(single)
          case _ => None
        }
      case _ => None
    }

  private def getFinderClassFqn(suiteTypeDef: ScTypeDefinition): Option[String] = {
    val finderAnnotationOpt = suiteTypeDef.annotations(FindersAnnotationFqn).headOption
    if (finderAnnotationOpt.isDefined && finderAnnotationOpt.get != null) {
      val annotation = finderAnnotationOpt.get
      try getFinderClassFqnFromAnnotation(annotation) catch {
        case e: Exception =>
          LOG.debug(
            s"""Failed to extract finder class name from annotation $annotation:
               |$e""".stripMargin
          )
          None
      }
    }
    else None
  }

  private def getFinderClassFqnFromAnnotation(annotation: ScAnnotation): Option[String] = {
    val constrInvocation = annotation.constructorInvocation
    if (constrInvocation != null) {
      val args = constrInvocation.args.orNull

      val annotationExpr = annotation.annotationExpr
      val valuePairs = annotationExpr.getAttributes

      if (args == null && valuePairs.nonEmpty)
        Some(valuePairs.head.getLiteralValue)
      else if (args != null) {
        args.exprs.headOption.flatMap {
          case assignment: ScAssignment =>
            getNameFromAnnotAssign(assignment)
          case call: ScMethodCall =>
            val expr = getExpressionFromAnnotCall(call)
            expr.flatMap(getNameFromAnnotLiteral)
          case expr =>
            getNameFromAnnotLiteral(expr)
        }
      }
      else None
    }
    else None
  }

  private def getTarget(className: String, element: PsiElement, selected: MethodInvocation): AstNode = {
    val firstChild = element.getFirstChild
    firstChild match {
      case literal: ScLiteral if literal.isString =>
        new StToStringTarget(firstChild, className, literal.getValue.toString)
      case invocation: MethodInvocation =>
        getScalaTestMethodInvocation(selected, invocation, Seq.empty, className) match {
          case Some(ast) => ast
          case _ => new StToStringTarget(firstChild, className, firstChild.getText)
        }
      case _ =>
        new StToStringTarget(firstChild, className, firstChild.getText)
    }
  }

  @tailrec
  private def getScalaTestMethodInvocation(selected: MethodInvocation,
                                           current: MethodInvocation,
                                           previousArgs: Seq[ScExpression],
                                           className: String): Option[StMethodInvocation] = {
    val arguments = current.argumentExpressions ++ previousArgs

    current.getInvokedExpr match {
      case ref: ScReferenceExpression =>
        val member = ref.resolve() match {
          case member: ScMember => Some(member)
          case pattern: ScBindingPattern =>
            pattern.nameContext match {
              case member: ScMember => Some(member)
              case _ => None
            }
          case _ => None
        }

        val containingClassName = member.flatMap(_.containingClass.toOption).map(_.qualifiedName).orNull

        val target: AstNode = getTarget(containingClassName, current, selected)

        val argsAst = arguments.map {
          case literal: ScLiteral if literal.isString =>
            new StStringLiteral(literal, containingClassName, literal.getValue.toString)
          case expr =>
            new StToStringTarget(expr, containingClassName, expr.getText)
        }

        val pName: String =
          if (current.isApplyOrUpdateCall) "apply"
          else ref.refName

        val nameSource: PsiElement =
          if (current.isApplyOrUpdateCall) null
          else ref

        Some(new StMethodInvocation(selected, containingClassName, target, pName, nameSource, argsAst.toArray))
      case invocation: MethodInvocation =>
        getScalaTestMethodInvocation(selected, invocation, arguments, className)
      case _ => None
    }
  }

  @tailrec
  private def getParentNode(className: String, element: PsiElement): AstNode = {
    element.getParent match {
      case parent: PsiElement =>
        transformNode(className, parent) match {
          case Some(parentAst) => parentAst
          case None => getParentNode(className, parent)
        }
      case _ => null
    }
  }

  private def getElementNestedBlockChildren(element: PsiElement): Seq[PsiElement] = {
    val children = element.getChildren.toSeq
    element match {
      case _: ScBlockExpr | _: ScTemplateBody =>
        children
      case _ =>
        children.flatMap {
          case argExprList: ScArgumentExprList =>
            argExprList.getChildren.headOption match {
              case Some(block: ScBlockExpr) => block.getChildren
              case _ => Seq.empty
            }
          case blockExpr: ScBlockExpr =>
            blockExpr.getChildren
          case (_: ScReferenceExpression) childOf (invocation: MethodInvocation) =>
            getTopInvocation(invocation).getLastChild.getLastChild.getChildren
          case _ =>
            Seq.empty
        }
    }
  }

  @tailrec
  private def getTopInvocation(element: MethodInvocation): MethodInvocation = {
    val invocationParent = element.getParent
    invocationParent match {
      case invocation: MethodInvocation => getTopInvocation(invocation)
      case _ => element
    }
  }

  private def getChildren(className: String, element: PsiElement): Array[AstNode] = {
    val nestedChildren = getElementNestedBlockChildren(element)
    nestedChildren.flatMap(transformNode(className, _).toSeq).toArray
  }

  private def transformNode(className: String, element: PsiElement): Option[AstNode] = element match {
    case invocation: MethodInvocation =>
      getScalaTestMethodInvocation(invocation, invocation, Seq.empty, className)
    case definition: ScFunctionDefinition =>
      getScalaTestMethodDefinition(definition)
    case o: ScObject =>
      o.extendsBlock.templateBody.map { tb =>
        new StModuleDefinition(o, tb, className, o.name.withoutBackticks.trim)
      }
    case td: ScTypeDefinition =>
      td.extendsBlock.templateBody.map { tb =>
        new StConstructorBlock(tb, className)
      }
    case _ => None
  }

  private def getScalaTestMethodDefinition(methodDef: ScFunctionDefinition): Option[StMethodDefinition] = {
    val containingClass = methodDef.containingClass
    if (containingClass != null) { // For inner method, this will be null
      val className = containingClass.qualifiedName
      val paramTypes = methodDef.parameters.flatMap(_.typeElement.map(_.getText)).toArray
      Some(new StMethodDefinition(methodDef, className, paramTypes.toSeq))
    } else {
      None // May be to build the nested AST nodes too
    }
  }

  private class StConstructorBlock(val element: PsiElement, pClassName: String)
    extends ConstructorBlock(pClassName, null, new Array[AstNode](0)) {

    override def children: Array[AstNode] = getChildren(pClassName, element)

    override def equals(other: Any): Boolean = other match {
      case o: StConstructorBlock => o.element == element
      case _ => false
    }

    override def hashCode: Int = element.hashCode
  }

  private class StModuleDefinition(val obj: ScObject, templateBody: ScTemplateBody, pClassName: String, name: String)
    extends ModuleDefinition(pClassName, null, new Array[AstNode](0), name) {

    override def parent: AstNode = getParentNode(className, obj)

    override def children: Array[AstNode] = getChildren(pClassName, templateBody)

    override def canBePartOfTestName: Boolean = !TestConfigurationUtil.isUnqualifiedPrivateOrThis(obj)

    override def equals(other: Any): Boolean = other match {
      case o: StModuleDefinition => o.obj == obj
      case _ => false
    }

    override def hashCode: Int = obj.hashCode
  }

  private class StMethodDefinition(val funDef: ScFunctionDefinition, pClassName: String, pParamTypes: Seq[String])
    extends MethodDefinition(pClassName, null, Array.empty, funDef.name.withoutBackticks.trim, pParamTypes: _*) {

    override def parent: AstNode = getParentNode(className, funDef)

    override def canBePartOfTestName: Boolean = !TestConfigurationUtil.isUnqualifiedPrivateOrThis(funDef)

    override def equals(other: Any): Boolean = other match {
      case o: StMethodDefinition => o.funDef == funDef
      case _ => false
    }

    override def hashCode: Int = funDef.hashCode
  }

  private class StMethodInvocation(val invocation: MethodInvocation,
                                   pClassName: String,
                                   pTarget: AstNode,
                                   pName: String,
                                   nameSource: PsiElement,
                                   override val args: Array[AstNode])
    extends org.scalatest.finders.MethodInvocation(pClassName, pTarget, null, new Array[AstNode](0), pName, args: _*) {

    override def parent: AstNode =  getParentNode(pClassName, invocation)

    override def children: Array[AstNode] = getChildren(pClassName, invocation)

    private def closestInvocationElement = PsiTreeUtil.getParentOfType(nameSource, classOf[MethodInvocation])

    override def canBePartOfTestName: Boolean =
      super.canBePartOfTestName && getStaticTestName(closestInvocationElement).isDefined

    override def equals(other: Any): Boolean = other match {
      case o: StMethodInvocation => invocation == o.invocation
      case _ => false
    }

    override def hashCode: Int = invocation.hashCode

    override def toString: String = getStaticTestName(closestInvocationElement).getOrElse(name)
  }

  private class StStringLiteral(val element: PsiElement,
                                pClassName: String,
                                pValue: String)
    extends StringLiteral(pClassName, null, pValue) {

    override def parent: AstNode = getParentNode(pClassName, element)

    override def equals(other: Any): Boolean = other match {
      case o: StStringLiteral => o.element == element
      case _ => false
    }

    override def hashCode: Int = element.hashCode

    override def canBePartOfTestName: Boolean = getStaticTestName(element).isDefined

    override def toString: String = getStaticTestName(element).getOrElse(super.toString)
  }

  private class StToStringTarget(val element: PsiElement, pClassName: String, target: Any)
    extends ToStringTarget(pClassName, null, new Array[AstNode](0), target) {

    private def isIt: Boolean =
      element.is[ScReferenceExpression] && target == "it" && itWordFqns.contains(pClassName)

    private def isThey: Boolean =
      element.is[ScReferenceExpression] && target == "they" && theyWordFqns.contains(pClassName)

    override def parent: AstNode = getParentNode(pClassName, element)

    override def children: Array[AstNode] = getChildren(pClassName, element)

    override def canBePartOfTestName: Boolean = isIt || isThey || getStaticTestName(element).isDefined

    override def toString: String =
      if (isIt || isThey) ""
      else getStaticTestName(element).getOrElse(name)

    override def equals(other: Any): Boolean = other match {
      case o: StToStringTarget => o.element == element
      case _ => false
    }

    override def hashCode: Int = element.hashCode
  }
}
