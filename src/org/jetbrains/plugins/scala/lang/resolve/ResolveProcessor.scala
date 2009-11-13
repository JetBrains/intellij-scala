package org.jetbrains.plugins.scala
package lang
package resolve

import scala.collection.mutable.{ListBuffer, ArrayBuffer}
import psi.api.base.patterns.ScReferencePattern
import psi.api.base.ScReferenceElement
import psi.api.statements._
import params.{ScTypeParam, ScParameter}
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.api.toplevel.ScTypedDefinition
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.Key

import psi.types._

import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.Set
import psi.api.base.types.ScTypeElement
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import psi.implicits.ScImplicitlyConvertible
import psi.api.expr.{ScMethodCall, ScGenericCall, ScExpression}
import result.{Success, TypingContext}
import scala._

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value], val name: String) extends BaseProcessor(kinds)
{
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      return named match {
        case o: ScObject if o.isPackageObject => true
        case _ => {
          candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
          false //todo
        }
      }
    }
    return true
  }

  protected def nameAndKindMatch(named: PsiNamedElement, state: ResolveState) = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) named.getName else nameSet
    elName == name && kindMatches(named)
  }

  override def getHint[T](hintKey: Key[T]): T = {
    if (hintKey == NameHint.KEY && name != "") ScalaNameHint.asInstanceOf[T]
    else super.getHint(hintKey)
  }

  object ScalaNameHint extends NameHint {
    def getName(state: ResolveState) = {
      val stateName = state.get(ResolverEnv.nameKey)
      if (stateName == null) name else stateName
    }
  }
}

class CollectAllProcessor(override val kinds: Set[ResolveTargets.Value], override val name: String)
        extends ResolveProcessor(kinds, name)
{
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
    true
  }
}

class RefExprResolveProcessor(kinds: Set[ResolveTargets.Value], name: String, typeArgElements: Seq[ScTypeElement])
        extends ResolveProcessor(kinds, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      def onlyImplicitParam(fd: ScFunctionDefinition) = {
        fd.allClauses.headOption.map(_.isImplicit) getOrElse false
      }
      named match {
        case fd: ScFunctionDefinition if onlyImplicitParam(fd) => {
          val s = getSubst(state)
          candidatesSet += new ScalaResolveResult(named, inferMethodTypesArgs(fd, s).followed(s), getImports(state));
          false
        }
        case m: PsiMethod if m.getParameterList.getParametersCount > 0 => true
        case fun: ScFun if fun.paramTypes.length > 0 => true
        case m: PsiMethod => {
          val s = getSubst(state)
          candidatesSet += new ScalaResolveResult(m, inferMethodTypesArgs(m, s).followed(s), getImports(state))
          false
        }
        case _ => {
          candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
          false //todo
        }
      }
    } else true
  }

  def inferMethodTypesArgs(m: PsiMethod, classSubst: ScSubstitutor) = {
    if (typeArgElements.length != 0)
    typeArgElements.map(_.getType(TypingContext.empty).getOrElse(Any)).zip(m.getTypeParameters).foldLeft(ScSubstitutor.empty) {
      (subst, pair) =>
              val scType = pair._1
              val typeParameter = pair._2
              subst.bindT(typeParameter.getName, scType)
    }
    else {
      m.getTypeParameters.foldLeft(ScSubstitutor.empty) {
        (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(tp match {
          case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, classSubst)
          case tp: PsiTypeParameter => new ScTypeParameterType(tp, classSubst)
        }))
      }
    }
  }
}

class MethodResolveProcessor(ref: PsiElement,
                             refName: String,
                             argumentClauses: List[Seq[ScExpression]],
                             typeArgElements: Seq[ScTypeElement],
                             expected: Option[ScType],
                             kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             noParentheses: Boolean = false,
                             section : Boolean = false) extends ResolveProcessor(kinds, refName) {

  // Return RAW types to not cycle while evaluating Parameter expected type
  // i.e. for functions return the most common type (Any, ..., Any) => Nothing
  private val args: Seq[ScType] = Nil //argumentClauses.map(_.cachedType)

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    if (nameAndKindMatch(named, state)) {
      val s = getSubst(state)
      element match {
        case m: PsiMethod => {
          val subst = inferMethodTypesArgs(m, s)
          candidatesSet += new ScalaResolveResult(m, subst.followed(s), getImports(state), None, implicitConversionClass)
          true
        }
        case cc: ScClass if cc.isCase => {
          val subst = inferMethodTypesArgs(cc, s)
          candidatesSet += new ScalaResolveResult(cc, subst.fos, getImports(state), None, implicitConversionClass) //todo add local type inference
          true
        }
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] => {
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            candidatesSet += new ScalaResolveResult(m, inferMethodTypesArgs(m, s).followed(s.followed(subst)),
              getImports(state), None, implicitConversionClass)
          }
          true
        }
        case _ => {
          candidatesSet += new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass)
          true
        }
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    def forFilter(c: ScalaResolveResult): Boolean = {
      val substitutor: ScSubstitutor = c.substitutor
      c.element match {
        case synthetic: ScSyntheticFunction if typeArgElements.length == 0 ||
                typeArgElements.length == synthetic.typeParameters.length => {
          Compatibility.compatible(synthetic, substitutor, argumentClauses)._1
        }
        case function: ScFunction if noParentheses && (typeArgElements.length == 0 ||
                typeArgElements.length == function.typeParameters.length) &&
                function.paramClauses.clauses.length == 1 && function.paramClauses.clauses.apply(0).isImplicit => true
        case function: ScFunction if typeArgElements.length == 0 ||
                typeArgElements.length == function.typeParameters.length => {
          Compatibility.compatible(new PhysicalSignature(function, substitutor), argumentClauses, section)._1
        }
        case method: PsiMethod if typeArgElements.length == 0 ||
                method.getTypeParameters.length == typeArgElements.length => {
          Compatibility.compatible(new PhysicalSignature(method, substitutor), argumentClauses, section)._1
        }
        case _ => false
      }
    }

    def forMap(c: ScalaResolveResult): ScalaResolveResult = {
      val substitutor: ScSubstitutor = c.substitutor
      c.element match {
        case synthetic: ScSyntheticFunction => {
          val s = Compatibility.compatible(synthetic, substitutor, argumentClauses)._2.getSubstitutor
          s match {
            case Some(s) => new ScalaResolveResult(synthetic, substitutor.followed(s), c.importsUsed, c.nameShadow, c.implicitConversionClass)
            case None => c
          }
        }
        case function: ScFunction => {
          var s = if (noParentheses && function.paramClauses.clauses.length == 1 &&
                  function.paramClauses.clauses.apply(0).isImplicit) new ScUndefinedSubstitutor
          else Compatibility.compatible(new PhysicalSignature(function, substitutor), argumentClauses, section)._2
          for (tParam <- function.typeParameters) { //todo: think about view type bound
            s = s.addLower(tParam.getName, substitutor.subst(tParam.lowerBound.getOrElse(Nothing)))
            s = s.addUpper(tParam.getName, substitutor.subst(tParam.upperBound.getOrElse(Any)))
          }
          (function.returnType, expected) match {
            case (Success(tp: ScType, _), Some(expected: ScType)) => {
              val rt = substitutor.subst(tp)
              if (rt.conforms(expected)) {
                val s2 = Conformance.undefinedSubst(expected, rt)
                s += s2
              }
            }
            case _ =>
          }
          s.getSubstitutor match {
            case Some(s) => new ScalaResolveResult(function, substitutor.followed(s), c.importsUsed, c.nameShadow, c.implicitConversionClass)
            case None => c
          }
        }
        case method: PsiMethod => {
          var s = Compatibility.compatible(new PhysicalSignature(method, substitutor), argumentClauses, section)._2
          for (tParam <- method.getTypeParameters) {
            s = s.addLower(tParam.getName, Nothing) //todo:
            s = s.addUpper(tParam.getName, Any) //todo:
          }
          (method.getReturnType, expected) match {
            case (pType: PsiType, Some(expected: ScType)) => {
              val rt = substitutor.subst(ScType.create(pType, ref.getProject))
              if (rt.conforms(expected)) {
                val s2 = Conformance.undefinedSubst(expected, rt)
                s += s2
              }
            }
            case _ =>
          }
          s.getSubstitutor match {
            case Some(s) => new ScalaResolveResult(method, substitutor.followed(s), c.importsUsed, c.nameShadow, c.implicitConversionClass)
            case None => c
          }
        }
      }
    }
    val applicable: ScalaResolveResult = candidatesSet.filter(forFilter(_)).map(forMap(_))

    if (applicable.isEmpty) candidatesSet.toArray else {
      val buff = new ArrayBuffer[ScalaResolveResult]
      def existsBetter(r: ScalaResolveResult): Boolean = {
        for (r1 <- applicable if r != r1) {
          if (isMoreSpecific(r1.element, r.element, r1.implicitConversionClass, r.implicitConversionClass)) return true
        }
        false
      }
      for (r <- applicable if !existsBetter(r)) {
        buff += r
      }
      buff.toArray[T]
    }
  }

  /**
   Pick all type parameters by method maps them to the appropriate type arguments, if they are
   */
  def inferMethodTypesArgs(m: PsiTypeParameterListOwner, classSubst: ScSubstitutor): ScSubstitutor = {
    if (typeArgElements.length != 0)
    typeArgElements.map(_.getType(TypingContext.empty).getOrElse(Any)).zip(m.getTypeParameters).foldLeft(ScSubstitutor.empty) {
      (subst, pair) =>
              val scType = pair._1
              val typeParameter = pair._2
              subst.bindT(typeParameter.getName, scType)
    }
    else {
      m.getTypeParameters.foldLeft(ScSubstitutor.empty) {
        (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(tp match {
          case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, classSubst)
          case tp: PsiTypeParameter => new ScTypeParameterType(tp, classSubst)
        }))
      }
    }
  }

  def isMoreSpecific(e1: PsiNamedElement, e2: PsiNamedElement, t1: Option[PsiClass], t2: Option[PsiClass]): Boolean = {
    (t1, t2) match {
      case (Some(t1), Some(t2)) => {
        if (t1.isInheritor(t2, true)) return true
      }
      case _ =>
    }
    (e1, e2, getType(e1), getType(e2)) match {
      case (e1, e2, ScFunctionType(ret1, params1), ScFunctionType(ret2, params2))
        if e1.isInstanceOf[PsiMethod] || e1.isInstanceOf[ScFun] => {
          val px = params1.zip(params2).map(p => Compatibility.compatible(p._2, p._1))
          val compt = px.foldLeft(true)((x: Boolean, z: Boolean) => x && z)
          Compatibility.compatible(ret1, ret2) && compt && params1.length == params2.length
      }
      case (_, e2: PsiMethod, _, _) => true
      case _ => Compatibility.compatible(getType(e1), getType(e2))
    }   
  }

  private def getType(e: PsiNamedElement): ScType = e match {
    case fun: ScFun => new ScFunctionType(fun.retType, collection.immutable.Seq(fun.paramTypes.toSeq: _*))
    case f: ScFunction => if (PsiTreeUtil.isAncestor(f, ref, true))
      new ScFunctionType(f.declaredType.getOrElse(Any), collection.immutable.Seq(f.paramTypes.toSeq: _*))
    else f.getType(TypingContext.empty).getOrElse(Any)
    case m: PsiMethod => ResolveUtils.methodType(m, ScSubstitutor.empty)

    case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
      case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, ref, true)) =>
        pd.declaredType match {case Some(t) => t; case None => Nothing}
      case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, ref, true)) =>
        vd.declaredType match {case Some(t) => t; case None => Nothing}
      case _ => refPatt.getType(TypingContext.empty).getOrElse(Any)
    }

    case typed: ScTypedDefinition => typed.getType(TypingContext.empty).getOrElse(Any)
    case _ => Nothing
  }

  override def changedLevel = candidatesSet.isEmpty //if there are any candidates, do not go upwards
}

import ResolveTargets._

object StdKinds {
  
  val stableQualRef = ValueSet(PACKAGE, OBJECT, VAL)
  val stableQualOrClass = stableQualRef + CLASS
  val noPackagesClassCompletion = ValueSet(OBJECT, VAL, CLASS)
  val stableImportSelector = ValueSet(OBJECT, VAL, VAR, METHOD, PACKAGE, CLASS)
  val stableClass = ValueSet(CLASS)

  val stableClassOrObject = ValueSet(CLASS, OBJECT)
  val classOrObjectOrValues = stableClassOrObject + VAL + VAR

  val refExprLastRef = ValueSet(OBJECT, VAL, VAR, METHOD)
  val refExprQualRef = refExprLastRef + PACKAGE

  val methodRef = ValueSet(VAL, VAR, METHOD)

  val valuesRef = ValueSet(VAL, VAR)

  val packageRef = ValueSet(PACKAGE)
}

object ResolverEnv {
  val nameKey: Key[String] = Key.create("ResolverEnv.nameKey")
}
