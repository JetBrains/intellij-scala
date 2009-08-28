package org.jetbrains.plugins.scala
package lang
package resolve

import collection.mutable.{ListBuffer, ArrayBuffer}
import psi.api.base.patterns.ScReferencePattern
import psi.api.base.ScReferenceElement
import psi.api.statements._
import psi.api.statements.params.ScParameter
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.api.toplevel.ScTyped
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.Key

import psi.types._

import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.Set
import psi.api.expr.ScExpression

class ResolveProcessor(override val kinds: Set[ResolveTargets.Value], val name: String) extends BaseProcessor(kinds)
{
  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      return named match {
        case o: ScObject if o.isPackageObject => true
        case _ => {
          candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
          return false //todo
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

class RefExprResolveProcessor(kinds: Set[ResolveTargets.Value], name: String)
        extends ResolveProcessor(kinds, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      def onlyImplicitParam(fd: ScFunctionDefinition) = {
        fd.allClauses.headOption.map(_.isImplicit) getOrElse false
      }
      named match {
        case fd: ScFunctionDefinition if onlyImplicitParam(fd) => {
          candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state));
          false
        }
        case m: PsiMethod if m.getParameterList.getParametersCount > 0 => true
        case fun: ScFun if fun.paramTypes.length > 0 => true
        case _ => candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state)); false //todo
      }
    } else true
  }
}

class MethodResolveProcessor(ref: ScReferenceElement, exprs: Seq[ScExpression],
                             expected: Option[ScType]) extends ResolveProcessor(StdKinds.methodRef, ref.refName) {

  // Return RAW types to not cycle while evaluating Parameter expected type
  // i.e. for functions return the most common type (Any, ..., Any) => Nothing
  private val args: Seq[ScType] = exprs.map(_.cachedType)

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val s = getSubst(state)
      element match {
        case m: PsiMethod => {
          candidatesSet += new ScalaResolveResult(m, s.incl(inferMethodTypesArgs(m, s)), getImports(state))
          true
        }
        case cc: ScClass if (cc.isCase) => {
          candidatesSet += new ScalaResolveResult(cc, s, getImports(state)) //todo add all constructors
          true
        }
        case o: ScObject => {
          for (m <- o.findMethodsByName("apply", true)) {
            candidatesSet += new ScalaResolveResult(m, s.incl(inferMethodTypesArgs(m, s)), getImports(state))
          }
          true
        }
        case _ => candidatesSet += new ScalaResolveResult(named, s, getImports(state));
        true
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult]: Array[T] = {
    val applicable = candidatesSet.filter {
      (c: ScalaResolveResult) => {
        val substitutor: ScSubstitutor = c.substitutor
        c.element match {
          case method: PsiMethod => {
            Compatibility.compatible(new PhysicalSignature(method, substitutor), exprs)
          }
          case _ => { //todo: for types you can use named parameters too
            val t = getType(c.element)
            t match {
              case ScFunctionType(ret, params) => {
                (expected match {
                  case None => true
                  case Some(t) => Compatibility.compatible(substitutor.subst(t), ret)
                }) && {
                  def checkCompatibility: Boolean = {
                    if (args.length < params.length) return false
                    if (args.length > params.length && !(c.element match {
                      case fun: ScFunction if fun.paramClauses.clauses.length > 0 => {
                        fun.paramClauses.clauses.apply(0).hasRepeatedParam
                      }
                      case _ => false //synthetic functions always not repeated
                    })) return false
                    for (i <- 0 to args.length - 1) {
                      if (i < params.length) {
                        if (!Compatibility.compatible(substitutor.subst(params(i)), args(i))) return false
                      }
                      else {
                        if (!Compatibility.compatible(substitutor.subst(params(params.length - 1)), args(i))) return false
                      }
                    }
                    return true
                  }
                  checkCompatibility
                }
              }
              case _ => false
            }
          }
        }
      }
    }
    if (applicable.isEmpty) candidatesSet.toArray else {
      val buff = new ArrayBuffer[ScalaResolveResult]
      def existsBetter(r: ScalaResolveResult): Boolean = {
        for (r1 <- applicable if r != r1) {
          if (isMoreSpecific(r1.element, r.element)) return true
        }
        false
      }
      for (r <- applicable if !existsBetter(r)) {
        buff += r
      }
      buff.toArray
    }
  }

  def inferMethodTypesArgs(m: PsiMethod, classSubst: ScSubstitutor) = ScSubstitutor.empty //todo

  def isMoreSpecific(e1: PsiNamedElement, e2: PsiNamedElement) = {
    val t1 = getType(e1)
    val t2 = getType(e2)
    e1 match {
      case _: PsiMethod | _: ScFun => {
        t1 match {
          case ScFunctionType(ret1, params1) => t2 match {
            case ScFunctionType(ret2, params2) => {
              val px = params1.zip(params2).map(p => Compatibility.compatible(p._2, p._1))
              val compt = px.foldLeft(true)((x: Boolean, z: Boolean) => x && z)
              Compatibility.compatible(ret1, ret2) && compt && params1.length == params2.length
            }
          }
        }
      }
      case _ => e2 match {
        case _: PsiMethod => true
        case _ => Compatibility.compatible(t1, t2)
      }
    }
  }

  private def getType(e: PsiNamedElement): ScType = e match {
    case fun: ScFun => new ScFunctionType(fun.retType, Seq(fun.paramTypes: _*))
    case f: ScFunction => if (PsiTreeUtil.isAncestor(f, ref, true))
      new ScFunctionType(f.declaredType, Seq(f.paramTypes: _*))
    else f.calcType
    case m: PsiMethod => ResolveUtils.methodType(m, ScSubstitutor.empty)

    case refPatt: ScReferencePattern => refPatt.getParent /*id list*/ .getParent match {
      case pd: ScPatternDefinition if (PsiTreeUtil.isAncestor(pd, ref, true)) =>
        pd.declaredType match {case Some(t) => t; case None => Nothing}
      case vd: ScVariableDefinition if (PsiTreeUtil.isAncestor(vd, ref, true)) =>
        vd.declaredType match {case Some(t) => t; case None => Nothing}
      case _ => refPatt.calcType
    }

    case typed: ScTyped => typed.calcType
    case _ => Nothing
  }

  override def changedLevel = candidatesSet.isEmpty //if there are any candidates, do not go upwards
}

import ResolveTargets._

object StdKinds {
  val stableQualRef = HashSet.empty + PACKAGE + OBJECT + VAL
  val stableQualOrClass = stableQualRef + CLASS
  val noPackagesClassCompletion = HashSet.empty + OBJECT + VAL + CLASS
  val stableImportSelector = HashSet.empty + OBJECT + VAL + VAR + METHOD + PACKAGE + CLASS
  val stableClass = HashSet.empty + CLASS

  val stableClassOrObject = HashSet.empty + CLASS + OBJECT
  val classOrObjectOrValues = stableClassOrObject + VAL + VAR

  val refExprLastRef = HashSet.empty + OBJECT + VAL + VAR + METHOD
  val refExprQualRef = refExprLastRef + PACKAGE

  val methodRef = HashSet.empty + VAL + VAR + METHOD

  val valuesRef = HashSet.empty + VAL + VAR

  val packageRef = HashSet.empty + PACKAGE
}

object ResolverEnv {
  val nameKey: Key[String] = Key.create("ResolverEnv.nameKey")
}
