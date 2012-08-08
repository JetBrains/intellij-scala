package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import collection.mutable.ListBuffer
import com.intellij.psi.PsiElement
import lang.psi.impl.expr.{ScBlockExprImpl, ScArgumentExprListImpl}
import annotation.tailrec
import lang.psi.api.base.patterns.ScBindingPattern
import lang.psi.api.statements.ScFunctionDefinition
import lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.execution.Location
import lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScTypeDefinition, ScMember}
import lang.psi.api.expr._
import org.scalatest.finders.{Finder, AstNode, ToStringTarget, Selection}
import lang.psi.api.base.ScLiteral
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{OrderRootType, ModuleRootManager}
import java.io.File
import java.net.{URLClassLoader, URL}
import lang.psi.impl.toplevel.typedef.MixinNodes
import lang.psi.types.ScType

/**
 * @author cheeseng
 * @since 16.01.2012
 */
class ScalaTestAstTransformer {
  def getFinder(clazz: ScClass, module: Module): Option[Finder] = {
    val clazzWithStyleOpt = MixinNodes.linearization(clazz).flatMap {
      tp => ScType.extractClass(tp, Some(clazz.getProject))
    }.find {
      psiClass =>
        psiClass match {
          case scClass: ScClass => scClass.hasAnnotation("org.scalatest.Style") != None
          case scTrait: ScTrait => scTrait.hasAnnotation("org.scalatest.Style") != None
          case _ => false
        }
    }

    def loadClass(className: String) = {
      val orderEntries = ModuleRootManager.getInstance(module).getOrderEntries
      val loaderUrls = orderEntries.map {
        orderEntry =>
          val rawUrls = orderEntry.getFiles(OrderRootType.CLASSES_AND_OUTPUT).map(_.getPresentableUrl)
          rawUrls.map {
            rawUrl =>
              val cpFile = new File(rawUrl)
              if (cpFile.exists && cpFile.isDirectory && !rawUrl.toString.endsWith(File.separator))
                new URL("file://" + rawUrl + "/")
              else
                new URL("file://" + rawUrl)
          }
      }.flatten
      val loader = new URLClassLoader(loaderUrls.toArray, getClass.getClassLoader)
      loader.loadClass(className)
    }

    clazzWithStyleOpt match {
      case Some(clazzWithStyle) => // Either ScClass or ScTrait, the following should be safe cast
        val typeDef = clazzWithStyle.asInstanceOf[ScTypeDefinition]
        try {
          val finderClassName: String = typeDef.hasAnnotation("org.scalatest.Style") match {
            case Some(styleAnnotation) =>
              val notFound = "NOT FOUND STYLE TEXT"
              styleAnnotation.constructor.args match {
                case Some(args) =>
                  val exprs = args.exprs
                  if (exprs.length > 0) {
                    exprs(0) match {
                      case l: ScLiteral if l.isString =>
                        val value = l.getValue
                        if (value.isInstanceOf[String]) value.asInstanceOf[String]
                        else notFound
                      case a: ScAssignStmt if a.getLExpression.isInstanceOf[ScReferenceExpression] &&
                        a.getLExpression.asInstanceOf[ScReferenceExpression].refName == "value" =>
                        a.getRExpression match {
                          case l: ScLiteral if l.isString =>
                            val value = l.getValue
                            if (value.isInstanceOf[String]) value.asInstanceOf[String]
                            else notFound
                          case _ => notFound
                        }
                      case _ => notFound
                    }
                  } else notFound
                case _ => notFound
              }
            case _ => throw new RuntimeException("Match is not exhaustive!")
          }
          Some(loadClass(finderClassName).newInstance.asInstanceOf[Finder])
        }
        catch {
          case _: Exception =>
            val suiteClassName = typeDef.qualifiedName
            val suiteClass = loadClass(suiteClassName)
            val styleOpt = suiteClass.getAnnotations.find(annt => annt.annotationType.getName == "org.scalatest.Style")
            styleOpt match {
              case Some(style) =>
                val valueMethod = style.annotationType.getMethod("value")
                val finderClassName = valueMethod.invoke(style).asInstanceOf[String]
                if (finderClassName != null) {
                  val finderClass = loadClass(finderClassName)
                  val instance = finderClass.newInstance
                  instance match {
                    case finder: Finder => Some(finder)
                    case _ => None
                  }
                }
                else
                  None
              case None => None
            }
        }
      case None =>
        None
    }
  }

  trait TreeSupport {
    def getParent(className: String, element: PsiElement): AstNode = {
      val elementParent = element.getParent
      if (elementParent == null) null
      else {
        val parentOpt = transformNode(className, elementParent)
        parentOpt match {
          case Some(parent) =>
            parent
          case None =>
            getParent(className, elementParent)
        }
      }
    }

    @tailrec
    final def getTopInvocation(element: MethodInvocation): PsiElement = {
      val invocationParent = element.getParent
      invocationParent match {
        case mi: MethodInvocation =>
          getTopInvocation(mi)
        case _ =>
          element
      }
    }

    def getChildren(className: String, element: PsiElement): Array[AstNode] = {
      def getElementNestedBlockChildren(element: PsiElement): Array[PsiElement] = {
        element match {
          case _: ScBlockExpr |
               _: ScTemplateBody =>
            element.getChildren
          case _ =>
            val nestedChildren = new ListBuffer[PsiElement]
            val children = element.getChildren
            for (child <- children) {
              child match {
                case argExprList: ScArgumentExprListImpl =>
                  val aelChildren = argExprList.getChildren
                  if (aelChildren.length > 0 && aelChildren(0).isInstanceOf[ScBlockExpr]) {
                    val blockExpr = aelChildren(0).asInstanceOf[ScBlockExpr]
                    nestedChildren ++= blockExpr.getChildren
                  }
                case blockExpr: ScBlockExprImpl =>
                  nestedChildren ++= blockExpr.getChildren
                case refExpr: ScReferenceExpression =>
                  refExpr.getParent match {
                    case refExprParentInvocation: MethodInvocation =>
                      nestedChildren ++= getTopInvocation(refExprParentInvocation).getLastChild.getLastChild.getChildren
                    case _ => // Do nothing for other types of node.
                  }
                case _ => // Do nothing for other types of node.
              }
            }
            nestedChildren.toArray
        }
      }
      val nestedChildren = getElementNestedBlockChildren(element)
      nestedChildren.map(transformNode(className, _)).filter(_.isDefined).map(_.get)
    }
  }

  private class StConstructorBlock(pClassName: String, val element: PsiElement)
    extends org.scalatest.finders.ConstructorBlock(pClassName, Array.empty) with TreeSupport {
    override lazy val children = getChildren(pClassName, element)

    override def equals(other: Any) = if (other != null && other.isInstanceOf[StConstructorBlock]) element eq other.asInstanceOf[StConstructorBlock].element else false

    override def hashCode = element.hashCode
  }

  private class StMethodDefinition(pClassName: String, val element: PsiElement, pName: String, pParamTypes: String*)
    extends org.scalatest.finders.MethodDefinition(pClassName, null, Array.empty, pName, pParamTypes.toList: _*) with TreeSupport {
    override lazy val parent: AstNode = getParent(className(), element)

    override lazy val children = getChildren(pClassName, element)

    override def equals(other: Any) = if (other != null && other.isInstanceOf[StMethodDefinition]) element eq other.asInstanceOf[StMethodDefinition].element else false

    override def hashCode = element.hashCode
  }

  private class StMethodInvocation(pClassName: String, pTarget: AstNode, val invocation: MethodInvocation, pName: String, pArgs: AstNode*)
    extends org.scalatest.finders.MethodInvocation(pClassName, pTarget, null, Array.empty, pName, pArgs.toList: _*) with TreeSupport {
    override lazy val parent: AstNode = getParent(pClassName, invocation)

    override lazy val children = getChildren(pClassName, invocation)

    override def equals(other: Any) = if (other != null && other.isInstanceOf[StMethodInvocation]) invocation eq other.asInstanceOf[StMethodInvocation].invocation else false

    override def hashCode = invocation.hashCode
  }

  private class StStringLiteral(pClassName: String, val element: PsiElement, pValue: String)
    extends org.scalatest.finders.StringLiteral(pClassName, null, pValue) with TreeSupport {
    override lazy val parent: AstNode = getParent(pClassName, element)

    override def equals(other: Any) = if (other != null && other.isInstanceOf[StStringLiteral]) element eq other.asInstanceOf[StStringLiteral].element else false

    override def hashCode = element.hashCode
  }

  private class StToStringTarget(pClassName: String, val element: PsiElement, target: AnyRef)
    extends org.scalatest.finders.ToStringTarget(pClassName, null, Array.empty, target) with TreeSupport {
    override lazy val parent: AstNode = getParent(pClassName, element)

    override lazy val children = getChildren(pClassName, element)

    override def equals(other: Any) = if (other != null && other.isInstanceOf[StToStringTarget]) element eq other.asInstanceOf[StToStringTarget].element else false

    override def hashCode = element.hashCode
  }

  def getSelectedAstNode(className: String, element: PsiElement): Option[AstNode] = {
    val astNodeOpt = transformNode(className, element)
    if (astNodeOpt.isDefined)
      astNodeOpt
    else {
      val parent = element.getParent
      if (parent == null)
        None
      else
        getSelectedAstNode(className, parent)
    }
  }

  def transformNode(className: String, element: PsiElement): Option[AstNode] = {
    def getScalaTestMethodInvocation(selected: MethodInvocation, current: MethodInvocation, currentParamsExpr: List[ScExpression] = List.empty): Option[StMethodInvocation] = {

      def getTarget(className: String, element: PsiElement): AstNode = {
        val firstChild = element.getFirstChild
        firstChild match {
          case lit: ScLiteral if lit.isString =>
            new ToStringTarget(className, null, Array.empty, lit.getValue.toString)
          case targetInvocation: MethodInvocation =>
            getScalaTestMethodInvocation(selected, targetInvocation).getOrElse(new ToStringTarget(className, null, Array.empty, firstChild.getText))
          case _ =>
            new ToStringTarget(className, null, Array.empty, firstChild.getText)
        }
      }

      val paramsExpr = current.argumentExpressions.toList ::: currentParamsExpr
      current.getInvokedExpr match {
        case ref: ScReferenceExpression =>
          val target: AstNode = try {
            getTarget(className, current)
          }
          catch {
            case _ => null
          }

          val resolve = ref.resolve()
          val containingClassName = if (resolve != null) {
            resolve match {
              case fun: ScMember => fun.containingClass.qualifiedName
              case p: ScBindingPattern =>
                p.nameContext match {
                  case v: ScMember => v.containingClass.qualifiedName
                  case _ => null
                }
              case _ => null
            }
          }
          else
            null

          val args: List[AstNode] = paramsExpr.map {
            expr =>
              expr match {
                case l: ScLiteral if l.isString =>
                  new StStringLiteral(containingClassName, expr, l.getValue.toString)
                case _ => new StToStringTarget(containingClassName, expr, expr.getText)
              }
          }
          Some(new StMethodInvocation(containingClassName, target, selected, if (current.isApplyOrUpdateCall) "apply" else ref.refName, args.toList: _*))
        case inv: MethodInvocation =>
          getScalaTestMethodInvocation(selected, inv, paramsExpr)
        case _ =>
          None
      }
    }

    def getScalaTestMethodDefinition(methodDef: ScFunctionDefinition): Option[StMethodDefinition] = {
      val containingClass = methodDef.containingClass
      if (containingClass != null) {
        // For inner method, this will be null
        val className = containingClass.qualifiedName
        val name = methodDef.name
        val paramTypes = methodDef.parameters.map {
          param => param.getType.getCanonicalText
        }

        Some(new StMethodDefinition(className, methodDef, name, paramTypes.toList: _*))
      }
      else
        None // May be to build the nested AST nodes too.
    }

    element match {
      case invocation: MethodInvocation =>
        getScalaTestMethodInvocation(invocation, invocation)
      case methodDef: ScFunctionDefinition =>
        getScalaTestMethodDefinition(methodDef)
      case constructor: ScTemplateBody =>
        new Some(new StConstructorBlock(className, constructor))
      case _ =>
        None
    }
  }

  def testSelection(location: Location[_ <: PsiElement]): Option[Selection] = {
    val element = location.getPsiElement
    val clazz = PsiTreeUtil.getParentOfType(element, classOf[ScClass], false)
    if (clazz == null) return None
    val finderOpt = getFinder(clazz, location.getModule)
    finderOpt match {
      case Some(finder) =>
        val selectedAstOpt = getSelectedAstNode(clazz.qualifiedName, element)
        selectedAstOpt match {
          case Some(selectedAst) =>
            //TODO add logging here
            /*selectedAst match {
              case org.scalatest.finders.MethodInvocation(className, target, parent, children, name, args) =>
                println("######parent: " + parent.getClass.getName)
              case _ =>
                println("######Other!!")
            }*/
            val selection = finder.find(selectedAst)
            /*selectionOpt match {
              case Some(selection) =>
                println("***Test Found, display name: " + selection.displayName() + ", test name(s):")
                selection.testNames.foreach(println(_))
              case None =>
                println("***Test Not Found!!")
            }*/
            if (selection != null)
              Some(selection)
            else
              None
          case None => None
        }
      case None => None
    }
  }
}