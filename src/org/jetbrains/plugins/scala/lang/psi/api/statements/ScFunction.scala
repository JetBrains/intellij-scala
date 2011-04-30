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
import org.jetbrains.plugins.scala.lang.psi.types.{Unit => UnitType}
import com.intellij.psi._

import psi.stubs.ScFunctionStub
import types._
import nonvalue._
import result.{Failure, Success, TypingContext, TypeResult}
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import expr.ScBlock
import psi.impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import base.ScMethodLike
import collection.immutable.Set
import java.lang.String

/**
 * @author Alexander Podkhalyuzin
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramClauses: Seq[Seq[Parameter]]

  def typeParameters: Seq[ScTypeParam]

  def methodType: ScType = {
    paramClauses.foldRight[ScType](retType) {
      (params: Seq[Parameter], tp: ScType) => new ScMethodType(tp, params, false)(getProject, getResolveScope)
    }
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
        with ScDeclaredElementsHolder with ScAnnotationsHolder with ScMethodLike {
  private var synth = false
  def setSynthetic() {
    synth = true
  }
  def isSyntheticCopy: Boolean = synth && name() == "copy"
  def isSyntheticApply: Boolean = synth && name() == "apply"

  def hasUnitResultType = {
    def hasUnitRT(t: ScType): Boolean = t match {
      case UnitType => true
      case ScMethodType(result, _, _) => hasUnitRT(result)
      case _ => false
    }
    hasUnitRT(methodType)
  }

  def isParameterless = paramClauses.clauses.isEmpty

  def isEmptyParen = paramClauses.clauses.size == 1 && paramClauses.params.size == 0

  def addEmptyParens() {
    val clause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
    paramClauses.addClause(clause)
  }

  def removeAllClauses() {
    paramClauses.clauses.headOption.zip(paramClauses.clauses.lastOption).foreach { p =>
      paramClauses.deleteChildRange(p._1, p._2)
    }
  }

  /**
   * This method is important for expected type evaluation.
   */
  def getInheritedReturnType: Option[ScType] = {
    returnTypeElement match {
      case Some(_) => returnType.toOption
      case None => {
        val superReturnType = superMethodAndSubstitutor match {
          case Some((fun: ScFunction, subst)) => fun.returnType.toOption.map(subst.subst)
          case Some((fun: ScSyntheticFunction, subst)) => Some(subst.subst(fun.retType))
          case Some((fun: PsiMethod, subst)) => Some(subst.subst(ScType.create(fun.getReturnType, getProject, getResolveScope)))
          case _ => None
        }
        superReturnType
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
        new ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)(getProject, getResolveScope)
      }
      else new ScMethodType(resultType, Seq.empty, false)(getProject, getResolveScope)
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

  def removeExplicitType() {
    val colon = children.find(_.getNode.getElementType == ScalaTokenTypes.tCOLON)
    (colon, returnTypeElement) match {
      case (Some(first), Some(last)) => deleteChildRange(first, last)
      case _ =>
    }
  }

  def paramClauses: ScParameters

  def isProcedure = paramClauses.clauses.isEmpty

  def returnType: TypeResult[ScType]

  def declaredType: TypeResult[ScType] = wrap(returnTypeElement) flatMap (_.getType(TypingContext.empty))

  def clauses: Option[ScParameters] = Some(paramClauses)

  def parameters: Seq[ScParameter]

  def paramTypes: Seq[ScType] = parameters.map {_.getType(TypingContext.empty).getOrElse(Nothing)}

  def syntheticParamClause: Option[ScParameterClause] = ScalaPsiUtil.syntheticParamClause(this, paramClauses, classParam = false)

  def declaredElements = Seq(this)

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superMethodAndSubstitutor: Option[(PsiMethod, ScSubstitutor)]

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

  def addParameter(param: ScParameter): ScFunction = {
    if (paramClauses.clauses.length > 0)
      paramClauses.clauses.apply(0).addParameter(param)
    else {
      val clause: ScParameterClause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
      val newClause = clause.addParameter(param)
      paramClauses.addClause(newClause)
    }
    return this
  }
}

object ScFunction {
  object Name {
    val Apply = "apply"
    val Update = "update"

    val Unapply = "unapply"
    val UnapplySeq = "unapplySeq"

    val Foreach = "foreach"
    val Map = "map"
    val FlatMap = "flatMap"
    val Filter = "filter"
    val WithFilter = "withFilter"

    val Unapplies: Set[String] = Set(Unapply, UnapplySeq)
    val ForComprehensions: Set[String] = Set(Foreach, Map, FlatMap, Filter, WithFilter)
    val Special: Set[String] = Set(Apply, Update) ++ Unapplies ++ ForComprehensions
  }

  /** Is this function sometimes invoked without it's name appearing at the call site? */
  def isSpecial(name: String): Boolean = Name.Special(name)
}