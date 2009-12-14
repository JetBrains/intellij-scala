package org.jetbrains.plugins.scala
package lang
package resolve

import psi.api.base.ScReferenceElement
import psi.api.statements._
import params.{ScTypeParam, ScParameter}
import psi.api.toplevel.typedef.{ScClass, ScObject}
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.Key

import psi.types._

import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.Set
import psi.api.base.types.ScTypeElement
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import result.{Success, TypingContext}
import scala._
import collection.mutable.{HashSet, ListBuffer, ArrayBuffer}
import psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import psi.api.expr.{ScTypedStmt, ScMethodCall, ScGenericCall, ScExpression}
import psi.implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}

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

class ExtractorResolveProcessor(ref: ScReferenceElement, refName: String, kinds: Set[ResolveTargets.Value],
                                expected: Option[ScType]/*, patternsCount: Int, lastSeq: Boolean*/)
        extends ResolveProcessor(kinds, refName) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      named match {
        case o: ScObject if o.isPackageObject => return true
        case clazz: ScClass if clazz.isCase => {
          candidatesSet.clear
          candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
          return false //find error  about existing unapply in companion during annotation under case class
        }
        case ta: ScTypeAliasDefinition => {
          val alType = ta.aliasedType(TypingContext.empty)
          for (tp <- alType) {
            ScType.extractClassType(tp) match {
              case Some((clazz: ScClass, subst: ScSubstitutor)) if clazz.isCase => {
                candidatesSet.clear
                candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
                return false
              }
              case _ =>
            }
          }
        }
        case obj: ScObject => {
          for (sign <- obj.signaturesByName("unapply")) {
            val m = sign.method
            val subst = sign.substitutor
            candidatesSet += new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state))
          }
          //unapply has bigger priority then unapplySeq
          if (candidatesSet.isEmpty)
          for (sign <- obj.signaturesByName("unapplySeq")) {
            val m = sign.method
            val subst = sign.substitutor
            candidatesSet += new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state))
          }
          return true
        }
        case _ => return true
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    //keep this in case if them want to do something about resolving to unapply methods
    /*def forFilter(c: ScalaResolveResult): Boolean = {
      val subst = c.substitutor
      c.element match {
        case c: ScClass => true
        case f: ScFunction if f.getName == "unapply" => {
          if (f.paramClauses.clauses.length == 0 || f.paramClauses.clauses.apply(0).parameters.length != 1) return false
          f.returnType match {
            case Success(tp: ScType, _) if tp.equiv(lang.psi.types.Boolean) => patternsCount == 0
            case Success(ScParameterizedType(tp, args), _) if args.length == 1 && (ScType.extractClassType(tp) match {
              case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Option" => true
              case _ => false
            }) => {
              args(0) match {
                case ScTupleType(args) => patternsCount == args.length
                case ScParameterizedType(tp, args) if (ScType.extractClassType(tp) match {
                  case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Tuple") => true
                  case _ => false
                }) => return patternsCount == args.length
                case tp => return patternsCount == 1
              }
            }
            case _ => return false
          }
        }
        case f: ScFunction if f.getName == "unapplySeq" => {
          if (f.paramClauses.clauses.length == 0 || f.paramClauses.clauses.apply(0).parameters.length != 1) return false
          f.returnType match {
            case Success(tp: ScType, _) if tp.equiv(lang.psi.types.Boolean) => patternsCount == 0
            case Success(ScParameterizedType(tp, args), _) if args.length == 1 && (ScType.extractClassType(tp) match {
              case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Option" => true
              case _ => false
            }) => {
              (Seq(args(0)) ++ BaseTypes.get(args(0))).find({
                case ScParameterizedType(des, args) if args.length == 1 && (ScType.extractClassType(des) match {
                  case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.collection.Seq" => true
                  case _ => false
                }) => true
                case _ => false
              }) match {
                case Some(ScParameterizedType(des, args)) => return true
                case _ => return false
              }
            }
            case _ => return false
          }
        }
        case _ => false
      }
    }*/
    //val applicable = candidatesSet.filter(forFilter(_))
    //if (applicable.isEmpty) return candidatesSet.toArray
    //todo: Local type inference
    expected match {
      case Some(tp) => {
          for (applicable <- candidatesSet) {
            applicable.element match {
              case fun: ScFunction => {
                val clauses = fun.paramClauses.clauses
                if (clauses.length != 0) {
                  if (clauses.apply(0).parameters.length == 1) {
                    for (paramType <- clauses(0).parameters.apply(0).getType(TypingContext.empty)) {
                      if (tp equiv applicable.substitutor.subst(paramType)) return Array(applicable)
                    }
                  }
                }
              }
              case _ =>
            }
          }
      }
      case _ =>
    }
    return candidatesSet.toArray
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

import Compatibility.Expression
class MethodResolveProcessor(ref: PsiElement,
                             refName: String,
                             argumentClauses: List[Seq[Expression]],
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
          //all this code for implicit overloading reesolution
          val res = new ScalaResolveResult(m, s.followed(subst), getImports(state), None, implicitConversionClass)
          (candidatesSet.find(p => p.hashCode == res.hashCode), implicitConversionClass) match {
            case (Some(oldRes: ScalaResolveResult), Some(newClass)) => {
              val oldClass = oldRes.implicitConversionClass
              oldClass match {
                case Some(clazz: PsiClass) if clazz.isInheritor(newClass, true) =>
                case _ => {
                  candidatesSet.remove(oldRes)
                  candidatesSet += res
                }
              }
            }
            case _ => candidatesSet += res
          }
          true
        }
        case cc: ScClass if cc.isCase => {
          val subst = inferMethodTypesArgs(cc, s)
          candidatesSet += new ScalaResolveResult(cc, s.followed(subst), getImports(state), None, implicitConversionClass)
          true
        }
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] => {
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            candidatesSet += new ScalaResolveResult(m, s.followed(subst.followed(inferMethodTypesArgs(m, s))),
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
    def forFilter(c: ScalaResolveResult, checkWithImplicits: Boolean): Boolean = {
      val substitutor: ScSubstitutor = c.substitutor
      c.element match {
        case tp: ScTypeParametersOwner if (typeArgElements.length == 0 ||
          typeArgElements.length == tp.typeParameters.length) && tp.isInstanceOf[PsiNamedElement] => {
          Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, argumentClauses, checkWithImplicits, ())._1
        }
        case tp: PsiTypeParameterListOwner if (typeArgElements.length == 0 ||
                typeArgElements.length == tp.getTypeParameters.length) &&
                tp.isInstanceOf[PsiNamedElement] => {
          Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, argumentClauses, checkWithImplicits, ())._1
        }
        case _ => false
      }
    }

    def forMap(c: ScalaResolveResult, withImplicits: Boolean): ScalaResolveResult = {
      if (typeArgElements.length != 0) return c
      val substitutor: ScSubstitutor = c.substitutor
      c.element match {
        case synthetic: ScSyntheticFunction => {
          val s = Compatibility.compatible(synthetic, substitutor, argumentClauses, withImplicits, ())._2.getSubstitutor
          s match {
            case Some(s) => new ScalaResolveResult(synthetic, substitutor.followed(s), c.importsUsed, c.nameShadow, c.implicitConversionClass)
            case None => c
          }
        }
        case owner: ScTypeParametersOwner => {
          var s = if (noParentheses && owner.isInstanceOf[ScParameterOwner] && owner.asInstanceOf[ScParameterOwner].allClauses.length == 1 &&
                  owner.asInstanceOf[ScParameterOwner].allClauses.apply(0).isImplicit) new ScUndefinedSubstitutor
          else {
            def argClauses: List[Seq[Expression]] = owner match {
              case fun: ScFunction => {
                val clauses = fun.paramClauses.clauses
                if (clauses.length - 1 != argumentClauses.length) argumentClauses
                else {
                  if (!clauses.apply(clauses.length - 1).isImplicit) argumentClauses
                  else {
                    //todo: check it's not anonymous function
                    val res = new ArrayBuffer[Expression]
                    for (param <- clauses(clauses.length - 1).parameters) {
                      val paramType = param.getType(TypingContext.empty).getOrElse(return argumentClauses)
                      val collector = new ImplicitParametersCollector(ref, substitutor.subst(paramType))
                      val results = collector.collect
                      if (results.length == 1) {
                        results(0) match {
                          case ScalaResolveResult(patt: ScBindingPattern, subst) => {
                            res += new Expression(subst.subst(patt.getType(TypingContext.empty).get))
                          }
                          case ScalaResolveResult(fun: ScFunction, subst) => {
                            val funType = {
                              if (fun.parameters.length == 0 || fun.paramClauses.clauses.apply(0).isImplicit) {
                                subst.subst(fun.getType(TypingContext.empty).get) match {
                                  case ScFunctionType(ret, _) => ret
                                  case x => x
                                }
                              }
                              else subst.subst(fun.getType(TypingContext.empty).get)
                            }
                            res += new Expression(funType)
                          }
                        }
                      } else return argumentClauses
                    }
                    argumentClauses ::: res.toSeq :: Nil
                  }
                }
              }
              case _ => argumentClauses  //todo: constructors
            }
            Compatibility.compatible(owner.asInstanceOf[PsiNamedElement], substitutor, argClauses, withImplicits, ())._2
          }
          for (tParam <- owner.typeParameters) { //todo: think about view type bound
            s = s.addLower(tParam.getName, substitutor.subst(tParam.lowerBound.getOrElse(Nothing)))
            s = s.addUpper(tParam.getName, substitutor.subst(tParam.upperBound.getOrElse(Any)))
          }
          owner match {
            case function: ScFunction => (function.returnType, expected) match {
              case (Success(tp: ScType, _), Some(expected: ScType)) => {
                val rt = substitutor.subst(tp)
                if (rt.conforms(expected)) {
                  val s2 = Conformance.undefinedSubst(expected, rt)
                  s += s2
                }
              }
              case _ =>
            }
            case _ => //todo: ?
          }
          s.getSubstitutor match {
            case Some(s) => new ScalaResolveResult(owner, substitutor.followed(s), c.importsUsed, c.nameShadow, c.implicitConversionClass)
            case None => c
          }
        }
        case owner: PsiTypeParameterListOwner => {
          var s = Compatibility.compatible(owner, substitutor, argumentClauses, withImplicits, ())._2
          for (tParam <- owner.getTypeParameters) {
            s = s.addLower(tParam.getName, Nothing) //todo:
            s = s.addUpper(tParam.getName, Any) //todo:
          }
          owner match {
            case method: PsiMethod => (method.getReturnType, expected) match {
              case (pType: PsiType, Some(expected: ScType)) => {
                val rt = substitutor.subst(ScType.create(pType, ref.getProject))
                if (rt.conforms(expected)) {
                  val s2 = Conformance.undefinedSubst(expected, rt)
                  s += s2
                }
              }
              case _ =>
            }
            case _ =>  //todo: ?
          }
          s.getSubstitutor match {
            case Some(s) => new ScalaResolveResult(owner, substitutor.followed(s), c.importsUsed, c.nameShadow, c.implicitConversionClass)
            case None => c
          }
        }
      }
    }
    var filtered = candidatesSet.filter(forFilter(_, false))
    val withImplicit = filtered.isEmpty
    if (filtered.isEmpty) filtered = candidatesSet.filter(forFilter(_, true)) //do not try implicit conversions if exists something without it
    val applicable: Set[ScalaResolveResult] = filtered.map(forMap(_, withImplicit))

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
      if (buff.length == 0) return candidatesSet.toArray
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
        else return false
      }
      case _ =>
    }
    (e1, e2, getType(e1), getType(e2)) match {
      case (e1, e2, ScFunctionType(ret1, params1), ScFunctionType(ret2, params2))
        if e1.isInstanceOf[PsiMethod] || e1.isInstanceOf[ScFun] => {
        import Compatibility.Expression
        val res = Compatibility.compatible(e2, e2 match {case m: PsiTypeParameterListOwner => inferMethodTypesArgs(m, ScSubstitutor.empty)
        case _ => ScSubstitutor.empty},
          List(params1.map(new Expression(_)) ++ Seq.fill(if (argumentClauses.length > 0 && params1.length > 0)
            0.max(argumentClauses.apply(0).length - params1.length) else 0)(new Expression(if (params1.length > 0) params1.last else Nothing))), 
          false, true)._1
        res
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
