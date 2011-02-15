package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import collection.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import psi.stubs.ScFunctionStub
import types._
import nonvalue._
import result.{Failure, Success, TypingContext, TypeResult}
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import expr.ScBlock

/**
 * @author Alexander Podkhalyuzin
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def parameters: Seq[Parameter]

  def typeParameters: Seq[ScTypeParam]

  def methodType: ScMethodType = {
    new ScMethodType(retType, parameters, false, getProject, getResolveScope)
  }

  def polymorphicType: ScType = {
    if (typeParameters.length == 0) return methodType
    else return ScTypePolymorphicType(methodType, typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp)))
  }
}


/**
 * Represents Scala's internal function definitions and declarations
 */
trait ScFunction extends ScalaPsiElement with ScMember with ScTypeParametersOwner
        with PsiMethod with ScParameterOwner with ScDocCommentOwner with ScTypedDefinition
        with ScDeclaredElementsHolder with ScAnnotationsHolder {
  private var synthCopy = false
  def isSyntheticCopy: Boolean = synthCopy
  def setSyntheticCopy: Unit = synthCopy = true

  /**
   * This method is important for expected type evaluation.
   */
  def getInheritedReturnType: Option[ScType] = {
    returnTypeElement match {
      case Some(_) => returnType.toOption
      case None => {
        superMethod match {
          case Some(fun: ScFunction) => fun.returnType.toOption
          case Some(fun: ScSyntheticFunction) => Some(fun.retType)
          case Some(fun: PsiMethod) => Some(ScType.create(fun.getReturnType, getProject, getResolveScope))
          case _ => None
        }
      }
    }
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset
  def hasParameterClause: Boolean = {
    if (paramClauses.clauses.length != 0) return true
    superMethod match {
      case Some(fun: ScFunction) => return fun.hasParameterClause
      case Some(psi: PsiMethod) => return true
      case None => return false
    }
  }

  def hasMalformedSignature = paramClauses.clauses.exists {
    _.parameters.dropRight(1).exists(_.isRepeatedParameter)
  }

  def definedReturnType: TypeResult[ScType] = {
    returnTypeElement match {
      case Some(ret) => ret.getType(TypingContext.empty)
      case _ if !hasAssign => return Success(Unit, Some(this))
      case _ => {
        superMethod match {
          case Some(f: ScFunction) => f.definedReturnType
          case Some(m: PsiMethod) => {
            Success(ScType.create(m.getReturnType, getProject, getResolveScope), Some(this))
          }
          case _ => Failure("No defined return type", Some(this))
        }
      }
    }
  }

  /**
   * Returns pure `function' type as it was defined as a field with functional value
   */
  def methodType: ScType = methodType(None)
  def methodType(result: Option[ScType]): ScType = {
    val parameters: ScParameters = paramClauses
    val clauses = parameters.clauses
    val resultType = result match {
      case None => returnType.getOrElse(Any)
      case Some(x) => x
    }
    if (!hasParameterClause) return resultType
    val res = if (clauses.length > 0)
      clauses.foldRight[ScType](resultType){(clause: ScParameterClause, tp: ScType) =>
        new ScMethodType(tp, clause.getSmartParameters, clause.isImplicit, getProject, getResolveScope)
      }
      else new ScMethodType(resultType, Seq.empty, false, getProject, getResolveScope)
    res.asInstanceOf[ScMethodType]
  }

  /**
   * Returns internal type with type parameters.
   */
  def polymorphicType: ScType = polymorphicType(None)
  def polymorphicType(result: Option[ScType]): ScType = {
    if (typeParameters.length == 0) return methodType(result)
    else return ScTypePolymorphicType(methodType(result), typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrElse(Nothing), tp.upperBound.getOrElse(Any), tp)))
  }

  /**
   * Optional Type Element, denotion function's return type
   * May be omitted for non-recursive functions
   */
  def returnTypeElement: Option[ScTypeElement] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          return stub.asInstanceOf[ScFunctionStub].getReturnTypeElement
        }
      }
      case _ =>
    }
    findChild(classOf[ScTypeElement])
  }

  def returnTypeIsDefined: Boolean = !definedReturnType.isEmpty

  def hasExplicitType = returnTypeElement.isDefined

  def paramClauses: ScParameters
  
  def isProcedure = paramClauses.clauses.isEmpty

  def returnType: TypeResult[ScType]

  def declaredType: TypeResult[ScType] = wrap(returnTypeElement) flatMap (_.getType(TypingContext.empty))

  def clauses: Option[ScParameters] = Some(paramClauses)

  def parameters: Seq[ScParameter]

  def paramTypes: Seq[ScType] = parameters.map {_.getType(TypingContext.empty).getOrElse(Nothing)}

  def declaredElements = Seq(this)

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superSignatures: Seq[FullSignature]

  def hasParamName(name: String, clausePosition: Int = -1): Boolean = getParamByName(name, clausePosition) != None

  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 => {
        for (param <- parameters if param.name == name) return Some(param)
        return None
      }
      case i if i < 0 => return None
      case i if i >= allClauses.length => return None
      case i => {
        val clause: ScParameterClause = allClauses.apply(i)
        for (param <- clause.parameters if param.name == name) return Some(param)
        return None
      }
    }
  }

  /**
   * Does the function have `=` between the signature and the implementation?
   */
  def hasAssign: Boolean
  
  override def accept(visitor: ScalaElementVisitor) = visitor.visitFunction(this)

  def getGetterOrSetterFunction: Option[ScFunction] = {
    getContainingClass match {
      case clazz: ScTemplateDefinition => {
        if (getName.endsWith("_=")) {
          clazz.functions.find(_.getName == getName.substring(0, getName.length - 2))
        } else if (!hasParameterClause) {
          clazz.functions.find(_.getName == getName + "_=")
        } else None
      }
      case _ => None
    }
  }

  /**
   * physical getContainingClass.
   */
  def containingClass: Option[ScTemplateDefinition] = {
    var parent = getParent
    while (parent != null) {
      parent match {
        case t: ScTemplateDefinition => return Some(t)
        case b: ScBlock => return None
        case _ => parent = parent.getParent
      }
    }
    return None
  }
}