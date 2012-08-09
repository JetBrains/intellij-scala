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
import expr.ScBlock
import psi.impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import base.ScMethodLike
import collection.immutable.Set
import caches.CachesUtil
import util.PsiModificationTracker
import psi.impl.toplevel.synthetic.{ScSyntheticTypeParameter, ScSyntheticFunction}
import java.lang.{ThreadLocal, String}
import extensions.toPsiNamedElementExt
import com.intellij.util.containers.ConcurrentHashMap
import light.ScFunctionWrapper

/**
 * @author Alexander Podkhalyuzin
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramClauses: Seq[Seq[Parameter]]

  def methodType: ScType = {
    paramClauses.foldRight[ScType](retType) {
      (params: Seq[Parameter], tp: ScType) => new ScMethodType(tp, params, false)(getProject, getResolveScope)
    }
  }

  def polymorphicType: ScType = {
    if (typeParameters.length == 0) methodType
    else ScTypePolymorphicType(methodType, typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp)))
  }
}


/**
 * Represents Scala's internal function definitions and declarations
 */
trait ScFunction extends ScalaPsiElement with ScMember with ScTypeParametersOwner
        with ScParameterOwner with ScDocCommentOwner with ScTypedDefinition
        with ScDeclaredElementsHolder with ScAnnotationsHolder with ScMethodLike {
  private var synth = false
  private var synthNavElement: Option[PsiElement] = None
  def setSynthetic(navElement: PsiElement) {
    synthNavElement = Some(navElement)
  }
  def isSyntheticCopy: Boolean = synthNavElement.nonEmpty && name == "copy"
  def isSyntheticApply: Boolean = synthNavElement.nonEmpty && name == "apply"
  def isSyntheticUnapply: Boolean = synthNavElement.nonEmpty && name == "unapply"
  def isSyntheticUnapplySeq: Boolean = synthNavElement.nonEmpty && name == "unapplySeq"
  def isSynthetic: Boolean = synthNavElement.nonEmpty
  def getSyntheticNavigationElement: Option[PsiElement] = synthNavElement

  def hasUnitResultType = {
    def hasUnitRT(t: ScType): Boolean = t match {
      case UnitType => true
      case ScMethodType(result, _, _) => hasUnitRT(result)
      case _ => false
    }
    hasUnitRT(methodType)
  }

  def isParameterless = paramClauses.clauses.isEmpty
  
  private val probablyRecursive: ThreadLocal[Boolean] = new ThreadLocal[Boolean]() {
    override def initialValue(): Boolean = false
  }
  def isProbablyRecursive = probablyRecursive.get()
  def setProbablyRecursive(b: Boolean) {probablyRecursive.set(b)}

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

  def isNative: Boolean = {
    hasAnnotation("scala.native") != None
  }

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "abstract") {
      containingClass match {
        case t: ScTrait => return true
        case _ =>
      }
    }
    super.hasModifierProperty(name)
  }

  /**
   * This method is important for expected type evaluation.
   */
  def getInheritedReturnType: Option[ScType] = {
    returnTypeElement match {
      case Some(_) => returnType.toOption
      case None => {
        val superReturnType = superMethodAndSubstitutor match {
          case Some((fun: ScFunction, subst)) =>
            var typeParamSubst = ScSubstitutor.empty
            fun.typeParameters.zip(typeParameters).foreach {
              case (oldParam: ScTypeParam, newParam: ScTypeParam) => {
                typeParamSubst = typeParamSubst.bindT((oldParam.name, ScalaPsiUtil.getPsiElementId(oldParam)),
                  new ScTypeParameterType(newParam, subst))
              }
            }
            fun.returnType.toOption.map(typeParamSubst.followed(subst).subst(_))
          case Some((fun: ScSyntheticFunction, subst)) =>
            var typeParamSubst = ScSubstitutor.empty
            fun.typeParameters.zip(typeParameters).foreach {
              case (oldParam: ScSyntheticTypeParameter, newParam: ScTypeParam) => {
                typeParamSubst = typeParamSubst.bindT((oldParam.name, ScalaPsiUtil.getPsiElementId(oldParam)),
                  new ScTypeParameterType(newParam, subst))
              }
            }
            Some(subst.subst(fun.retType))
          case Some((fun: PsiMethod, subst)) =>
            var typeParamSubst = ScSubstitutor.empty
            fun.getTypeParameters.zip(typeParameters).foreach {
              case (oldParam: PsiTypeParameter, newParam: ScTypeParam) => {
                typeParamSubst = typeParamSubst.bindT((oldParam.name, ScalaPsiUtil.getPsiElementId(oldParam)),
                  new ScTypeParameterType(newParam, subst))
              }
            }
            Some(typeParamSubst.followed(subst).subst(ScType.create(fun.getReturnType, getProject, getResolveScope)))
          case _ => None
        }
        superReturnType
      }
    }
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset
  def hasParameterClause: Boolean = {
    if (effectiveParameterClauses.length != 0) return true
    superMethod match {
      case Some(fun: ScFunction) => fun.hasParameterClause
      case Some(psi: PsiMethod) => true
      case None => false
    }
  }

  /**
   * Signature has repeated param, which is not the last one
   */
  def hasMalformedSignature: Boolean = {
    val clausesIterator = paramClauses.clauses.iterator
    while (clausesIterator.hasNext) {
      val clause = clausesIterator.next()
      val paramsIterator = clause.parameters.iterator
      while (paramsIterator.hasNext) {
        val param = paramsIterator.next()
        if (paramsIterator.hasNext && param.isRepeatedParameter) return true
      }
    }
    false
  }

  def definedReturnType: TypeResult[ScType] = {
    returnTypeElement match {
      case Some(ret) => ret.getType(TypingContext.empty)
      case _ if !hasAssign => Success(Unit, Some(this))
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
   * Returns pure 'function' type as it was defined as a field with functional value
   */
  def methodType(result: Option[ScType]): ScType = {
    val clauses = effectiveParameterClauses
    val resultType = result match {
      case None => returnType.getOrAny
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
    if (typeParameters.length == 0) methodType(result)
    else ScTypePolymorphicType(methodType(result), typeParameters.map(tp =>
      TypeParameter(tp.name, tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp)))
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

  def parameterList: ScParameters = paramClauses // TODO merge

  def isProcedure = paramClauses.clauses.isEmpty

  def returnType: TypeResult[ScType]

  def declaredType: TypeResult[ScType] = wrap(returnTypeElement) flatMap (_.getType(TypingContext.empty))

  def clauses: Option[ScParameters] = Some(paramClauses)

  def parameters: Seq[ScParameter]

  def paramTypes: Seq[ScType] = parameters.map {_.getType(TypingContext.empty).getOrNothing}

  def effectiveParameterClauses: Seq[ScParameterClause] = {
    CachesUtil.get(this, CachesUtil.FUNCTION_EFFECTIVE_PARAMETER_CLAUSE_KEY,
      new CachesUtil.MyProvider(this, (f: ScFunction) => f.paramClauses.clauses ++ f.syntheticParamClause)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  private def syntheticParamClause: Option[ScParameterClause] = {
    val hasImplicit = clauses.exists(_.clauses.exists(_.isImplicit))
    if (isConstructor) {
      containingClass match {
        case owner: ScTypeParametersOwner =>
          if (hasImplicit) None else ScalaPsiUtil.syntheticParamClause(owner, paramClauses, classParam = false)
        case _ => None
      }
    } else {
      if (hasImplicit) None else ScalaPsiUtil.syntheticParamClause(this, paramClauses, classParam = false)
    }
  }

  def declaredElements = Seq(this)

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superMethodAndSubstitutor: Option[(PsiMethod, ScSubstitutor)]

  def superSignatures: Seq[Signature]

  def superSignaturesIncludingSelfType: Seq[Signature]

  def hasParamName(name: String, clausePosition: Int = -1): Boolean = getParamByName(name, clausePosition) != None

  /**
   * Seek parameter with appropriate name in appropriate parameter clause.
   * @param name parameter name
   * @param clausePosition = -1, effective clause number, if -1 then parameter in any explicit? clause
   */
  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 => {
        for (param <- parameters if ScalaPsiUtil.memberNamesEquals(param.name, name)) return Some(param)
        None
      }
      case i if i < 0 => None
      case i if i >= effectiveParameterClauses.length => None
      case i => {
        val clause: ScParameterClause = effectiveParameterClauses.apply(i)
        for (param <- clause.parameters if ScalaPsiUtil.memberNamesEquals(param.name, name)) return Some(param)
        None
      }
    }
  }

  /**
   * Does the function have `=` between the signature and the implementation?
   */
  def hasAssign: Boolean

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitFunction(this)
  }

  def getGetterOrSetterFunction: Option[ScFunction] = {
    containingClass match {
      case clazz: ScTemplateDefinition => {
        if (name.endsWith("_=")) {
          clazz.functions.find(_.name == name.substring(0, name.length - 2))
        } else if (!hasParameterClause) {
          clazz.functions.find(_.name == name + "_=")
        } else None
      }
      case _ => None
    }
  }

  def isBridge: Boolean = {
    //todo: fix algorithm for annotation resolve to not resolve objects (if it's possible)
    //heuristic algorithm to avoid SOE in MixinNodes.build
    annotations.find(annot => {
      annot.typeElement match {
        case s: ScSimpleTypeElement => s.reference match {
          case Some(ref) => ref.refName == "bridge"
          case _ => false
        }
        case _ => false
      }
    }) != None
  }

  def addParameter(param: ScParameter): ScFunction = {
    if (paramClauses.clauses.length > 0)
      paramClauses.clauses.apply(0).addParameter(param)
    else {
      val clause: ScParameterClause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
      val newClause = clause.addParameter(param)
      paramClauses.addClause(newClause)
    }
    this
  }

  private var functionWrapper: ConcurrentHashMap[(Boolean, Boolean, Option[PsiClass]), (ScFunctionWrapper, Long)] =
    new ConcurrentHashMap()

  def getFunctionWrapper(isStatic: Boolean, isInterface: Boolean, cClass: Option[PsiClass] = None): ScFunctionWrapper = {
    val curModCount = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    val r = functionWrapper.get(isStatic, isInterface, cClass)
    if (r != null && r._2 == curModCount) {
      return r._1
    }
    val res = new ScFunctionWrapper(this, isStatic, isInterface, cClass)
    functionWrapper.put((isStatic, isInterface, cClass), (res, curModCount))
    res
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