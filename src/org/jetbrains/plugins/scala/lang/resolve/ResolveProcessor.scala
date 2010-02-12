package org.jetbrains.plugins.scala
package lang
package resolve

import psi.api.base.ScReferenceElement
import psi.api.statements._
import params.{ScTypeParam, ScParameter}
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.Key

import psi.types._

import _root_.scala.collection.immutable.HashSet
import nonvalue.{Parameter, TypeParameter, ScTypePolymorphicType, ScMethodType}
import psi.api.base.types.ScTypeElement
import result.{Success, TypingContext}
import scala._
import collection.mutable.{HashSet, ListBuffer, ArrayBuffer}
import collection.{Seq, Set}
import psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import psi.api.expr.{ScTypedStmt, ScMethodCall, ScGenericCall, ScExpression}
import psi.implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import psi.ScalaPsiUtil
import psi.api.toplevel.typedef.{ScTypeDefinition, ScClass, ScObject}
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import psi.impl.toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value], val ref: PsiElement, val name: String) extends BaseProcessor(kinds)
{
  def isAccessible(named: PsiNamedElement, place: PsiElement): Boolean = {
    val memb: PsiMember = {
      named match {
        case memb: PsiMember => memb
        case pl => ScalaPsiUtil.nameContext(named) match {
          case memb: PsiMember => memb
          case _ => return true //something strange
        }
      }
    }
    return ResolveUtils.isAccessible(memb, place)
  }

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
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
    val elName = if (nameSet == null) named.getName.replace("`", "") else nameSet.replace("`", "")
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
        extends ResolveProcessor(kinds, ref, refName) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
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
        case bind: ScBindingPattern => {
          candidatesSet += new ScalaResolveResult(bind, getSubst(state), getImports(state))
        }
        case _ => return true
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
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

/**
 * This class is useful for finding actual methods for unapply or unapplySeq, in case for values:
 * <code>
 *   val a: Regex
 *   z match {
 *     case a() =>
 *   }
 * </code>
 * This class cannot be used for actual resolve, because reference to value should work to this value, not to
 * invoked unapply method.
 */
class ExpandedExtractorResolveProcessor(ref: ScReferenceElement, refName: String, kinds: Set[ResolveTargets.Value],
        expected: Option[ScType]) extends ExtractorResolveProcessor(ref, refName, kinds, expected) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return false
      named match {
        case bind: ScBindingPattern => {
          ScalaPsiUtil.nameContext(bind) match {
            case v: ScValue => {
              val parentSubst = getSubst(state)
              val typez = bind.getType(TypingContext.empty).getOrElse(return true)
              ScType.extractClassType(typez) match {
                case Some((clazz: ScTypeDefinition, substitutor: ScSubstitutor)) => {
                  for (sign <- clazz.signaturesByName("unapply")) {
                    val m = sign.method
                    val subst = sign.substitutor
                    candidatesSet += new ScalaResolveResult(m, parentSubst.followed(substitutor).followed(subst),
                      getImports(state))
                  }
                  //unapply has bigger priority then unapplySeq
                  if (candidatesSet.isEmpty)
                  for (sign <- clazz.signaturesByName("unapplySeq")) {
                    val m = sign.method
                    val subst = sign.substitutor
                    candidatesSet += new ScalaResolveResult(m, parentSubst.followed(substitutor).followed(subst),
                      getImports(state))
                  }
                  return true
                }
                case _ => return true
              }
            }
            case _ => return true
          }
        }
        case _ => return super.execute(element, state)
      }
    }
    true
  }
}

class CollectAllProcessor(override val kinds: Set[ResolveTargets.Value], override val ref: PsiElement,
                          override val name: String)
        extends ResolveProcessor(kinds, ref, name)
{
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
    }
    true
  }
}

import Compatibility.Expression
class MethodResolveProcessor(override val ref: PsiElement,
                             refName: String,
                             argumentClauses: List[Seq[Expression]],
                             typeArgElements: Seq[ScTypeElement],
                             expected: => Option[ScType],
                             kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             noParentheses: Boolean = false,
                             section : Boolean = false) extends ResolveProcessor(kinds, ref, refName) {

  /**
   * Contains highest precedence of all resolve results.
   * 1 - import a._
   * 2 - import a.x
   * 3 - definition or declaration
   */
  private var precedence: Int = 0

  private val levelSet: collection.mutable.HashSet[ScalaResolveResult] = new collection.mutable.HashSet

  /**
   * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
   */
  private def addResult(result: ScalaResolveResult): Boolean = {
    val currentPrecedence = getPrecendence(result)
    if (currentPrecedence < precedence) return false
    else if (currentPrecedence == precedence && levelSet.isEmpty) return false
    precedence = currentPrecedence
    val newSet = levelSet.filterNot(res => getPrecendence(res) < precedence)
    levelSet.clear
    levelSet ++= newSet
    levelSet += result
    true
  }

  private def getPrecendence(result: ScalaResolveResult): Int = {
    if (result.importsUsed.size == 0) {
      ScalaPsiUtil.nameContext(result.getElement) match {
        case synthetic: ScSyntheticClass => return 1
        case clazz: PsiClass => {
          val qualifier = clazz.getQualifiedName
          if (qualifier == null) return 5
          val index: Int = qualifier.lastIndexOf('.')
          if (index == -1) return 5
          val q = qualifier.substring(0, index)
          if (q == "java.lang") return 1
          else if (q == "scala") return 2
          else return 5
        }
        case _: ScBindingPattern | _: PsiMember => {
          val clazz = PsiTreeUtil.getParentOfType(result.getElement, classOf[PsiClass])
          if (clazz == null) return 5
          else {
            clazz.getQualifiedName match {
              case "scala.Predef" => return 2
              case "scala.LowPriorityImplicits" => return 2
              case "scala" => return 2
              case _ => return 5
            }
          }
        }
        case _ => 
      }
      return 5
    }
    val importUsed: ImportUsed = result.importsUsed.toSeq.apply(0)
    importUsed match {
      case _: ImportWildcardSelectorUsed => return 3
      case _: ImportSelectorUsed => return 4
      case ImportExprUsed(expr) => {
        if (expr.singleWildcard) return 3
        else return 4
      }
    }
  }

  override def changedLevel = {
    if (levelSet.isEmpty) true
    else if (precedence == 5) {
      candidatesSet ++= levelSet
      levelSet.clear
      false
    }
    else {
      candidatesSet ++= levelSet
      levelSet.clear
      true
    }
  }

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY) match {
      case null => None
      case x => Some(x)
    }
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      val s = getSubst(state)
      element match {
        case m: PsiMethod => {
          //all this code for implicit overloading reesolution
          //todo: this is bad code, should be rewrited
          val res = new ScalaResolveResult(m, s, getImports(state), None, implicitConversionClass)
          ((candidatesSet ++ levelSet).find(p => p.hashCode == res.hashCode), implicitConversionClass) match {
            case (Some(oldRes: ScalaResolveResult), Some(newClass)) => {
              val oldClass = oldRes.implicitConversionClass
              oldClass match {
                case Some(clazz: PsiClass) if clazz.isInheritor(newClass, true) =>
                case _ => {
                  candidatesSet.remove(oldRes)
                  levelSet.remove(oldRes)
                  levelSet += res
                }
              }
            }
            case _ => addResult(res)
          }
          true
        }
        case cc: ScClass if cc.isCase && ref.getParent.isInstanceOf[ScMethodCall] ||
                ref.getParent.isInstanceOf[ScGenericCall] => {
          addResult(new ScalaResolveResult(cc, s, getImports(state), None, implicitConversionClass))
          true
        }
        case cc: ScClass if cc.isCase && !ref.getParent.isInstanceOf[ScReferenceElement] &&
                ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(cc.constructor.getOrElse(return true), s, getImports(state), None,
            implicitConversionClass))
          true
        }
        case cc: ScClass if cc.isCase && ScalaPsiUtil.getCompanionModule(cc) == None => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass))
        }
        case cc: ScClass => true
        case o: ScObject if o.isPackageObject => {
          addResult(new ScalaResolveResult(o, s, getImports(state), None, implicitConversionClass))
          return true
        }
        case o: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] => {
          for (sign: PhysicalSignature <- o.signaturesByName("apply")) {
            val m = sign.method
            val subst = sign.substitutor
            addResult(new ScalaResolveResult(m, s.followed(subst), getImports(state), None, implicitConversionClass))
          }
          true
        }
        case synthetic: ScSyntheticFunction => {
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), None, implicitConversionClass))
        }
        case _ => {
          addResult(new ScalaResolveResult(named, s, getImports(state), None, implicitConversionClass))
          true
        }
      }
    }
    return true
  }

  private def forFilter(c: ScalaResolveResult, checkWithImplicits: Boolean): Boolean = {
    val substitutor: ScSubstitutor =
    getType(c.element) match {
      case ScTypePolymorphicType(_, typeParams) => {
        val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
          (subst: ScSubstitutor, tp: TypeParameter) =>
            subst.bindT(tp.name, new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
        }
        c.substitutor.followed(s)
      }
      case _ => c.substitutor
    }
    c.element match {
      case fun: ScFunction  if (typeArgElements.length == 0 ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.apply(0).isImplicit && argumentClauses.length == 0 => true //special case for cases like Seq.toArray
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

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    val set = candidatesSet ++ levelSet
    var filtered = set.filter(forFilter(_, false))
    val withImplicit = filtered.isEmpty
    if (filtered.isEmpty) filtered = set.filter(forFilter(_, true)) //do not try implicit conversions if exists something without it
    val applicable: Set[ScalaResolveResult] = filtered

    if (applicable.isEmpty) set.toArray else {
      mostSpecific(applicable) match {
        case Some(r) => Array(r)
        case None => applicable.toArray
      }
    }
  }

  private def isAsSpecificAs(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean = {
    def lastRepeated(params: Seq[Parameter]): Boolean = {
      params.lastOption.getOrElse(return false).isRepeated
    }
    (r1.element, r2.element) match {
      case (m1@(_: PsiMethod | _: ScFun), m2@(_: PsiMethod | _: ScFun)) => {
        val (t1, t2) = (getType(m1), getType(m2))
        def calcParams(tp: ScType): Seq[Parameter] = {
          tp match {
            case ScMethodType(_, params, _) => params
            case ScTypePolymorphicType(ScMethodType(_, params, _), typeParams) => {
              val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
                (subst: ScSubstitutor, tp: TypeParameter) =>
                  subst.bindT(tp.name, new ScExistentialArgument(tp.name, List.empty, tp.lowerType, tp.upperType))
              }
              params.map(p => Parameter(p.name, s.subst(p.paramType), p.isDefault, p.isRepeated))
            }
            case _ => Seq.empty
          }
        }
        val (params1, params2) = (calcParams(t1), calcParams(t2))
        if (lastRepeated(params1) && !lastRepeated(params2)) return false
        val i: Int = if (argumentClauses.length > 0 && params1.length > 0) 0.max(argumentClauses.apply(0).
                length - params1.length) else 0
        val default: Expression = new Expression(if (params1.length > 0) params1.last.paramType else Nothing)
        val exprs: Seq[Expression] = params1.map(p => new Expression(p.paramType)) ++ Seq.fill(i)(default)
        return Compatibility.checkConformance(false, params2, exprs, false)._1
      }
      case (_, m2: PsiMethod) => return true
      case (e1, e2) => return Compatibility.compatible(getType(e1), getType(e2))
    }
  }

  private def getClazz(r: ScalaResolveResult): Option[PsiClass] = {
    val element = ScalaPsiUtil.nameContext(r.element)
    element match {
      case memb: PsiMember => {
        val clazz = memb.getContainingClass
        if (clazz == null) None else Some(clazz)
      }
      case _ => None
    }
  }

  private def isDerived(c1: Option[PsiClass], c2: Option[PsiClass]): Boolean = {
    (c1, c2) match {
      case (Some(c1), Some(c2)) => {
        if (c1 == c2) return false
        if (c1.isInheritor(c2, true)) return true
        ScalaPsiUtil.getCompanionModule(c1) match {
          case Some(c1) => if (c1.isInheritor(c2, true)) return true
          case _ =>
        }
        ScalaPsiUtil.getCompanionModule(c2) match {
          case Some(c2) => if (c1.isInheritor(c2, true)) return true
          case _ =>
        }
        return false
      }
      case _ => false
    }
  }

  private def relativeWeight(r1: ScalaResolveResult, r2: ScalaResolveResult): Int = {
    val s1 = if (isAsSpecificAs(r1, r2)) 1 else 0
    val s2 = if (isDerived(getClazz(r1), getClazz(r2))) 1 else 0
    s1 + s2
  }

  private def isMoreSpecific(r1: ScalaResolveResult, r2: ScalaResolveResult): Boolean = {
    (r1.implicitConversionClass, r2.implicitConversionClass) match {
      case (Some(t1), Some(t2)) => if (t1.isInheritor(t2, true)) return true
      case _ =>
    }
    relativeWeight(r1, r2) > relativeWeight(r2, r1)
  }

  private def mostSpecific(applicable: Set[ScalaResolveResult]): Option[ScalaResolveResult] = {
    for (a1 <- applicable) {
      var break = false
      for (a2 <- applicable if a1 != a2 && !break) {
        if (!isMoreSpecific(a1, a2)) break = true
      }
      if (!break) return Some(a1)
    }
    return None
  }

  //todo: implement existential dual
  private def getType(e: PsiNamedElement): ScType = e match {
    case fun: ScFun => fun.polymorphicType
    case f: ScFunction => f.polymorphicType
    case m: PsiMethod => ResolveUtils.javaPolymorphicType(m, ScSubstitutor.empty)
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
