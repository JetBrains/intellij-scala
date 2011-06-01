package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import api.toplevel.imports.usages.ImportUsed
import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.{PsiTreeUtil, CachedValue, PsiModificationTracker}
import lang.resolve.{ScalaResolveResult, ResolveTargets}

import types._
import _root_.scala.collection.Set
import result.TypingContext
import com.intellij.psi._
import collection.mutable.{ArrayBuffer, HashMap, HashSet}
import lang.resolve.processor.BaseProcessor
import api.expr.{ScTypedStmt, ScExpression}
import api.base.patterns.ScBindingPattern
import api.statements._
import api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.typedef._
import params.{ScParameter, ScTypeParam}
import psi.util.CommonClassesSearcher

/**
 * @author ilyas
 *
 * Mix-in implementing functionality to collect [and possibly apply] implicit conversions
 */

trait ScImplicitlyConvertible extends ScalaPsiElement {
  self: ScExpression =>

  /**
   * Get all implicit types for given expression
   */
  def getImplicitTypes : List[ScType] = {
      implicitMap().map(_._1).toList
  }

  /**
   * returns class which contains function for implicit conversion to type t.
   */
  def getClazzForType(t: ScType): Option[PsiClass] = {
    implicitMap().find(tp => t.equiv(tp._1)) match {
      case Some((_, fun, _)) => {
        fun.getParent match {
          case tb: ScTemplateBody => Some(PsiTreeUtil.getParentOfType(tb, classOf[PsiClass]))
          case _ => None
        }
      }
      case _ => None
    }
  }

  /**
   *  Get all imports used to obtain implicit conversions for given type
   */
  def getImportsForImplicit(t: ScType): Set[ImportUsed] = {
    implicitMap().find(tp => t.equiv(tp._1)).map(s => s._3) match {
      case Some(s) => s
      case None => Set()
    }
  }

  @volatile
  private var cachedImplicitMap: Seq[(ScType, PsiNamedElement, Set[ImportUsed])] = null

  @volatile
  private var modCount: Long = 0

  def implicitMap(exp: Option[ScType] = None,
                  fromUnder: Boolean = false): Seq[(ScType, PsiNamedElement, Set[ImportUsed])] = {
    exp match {
      case Some(expected) => return buildImplicitMap(exp, fromUnder)
      case None =>
    }
    if (fromUnder) return buildImplicitMap(exp, fromUnder)
    var tp = cachedImplicitMap
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && modCount == curModCount) {
      return tp
    }
    tp = buildImplicitMap()
    cachedImplicitMap = tp
    modCount = curModCount
    tp
  }

  private def buildImplicitMap(exp: Option[ScType] = expectedType,
                               fromUnder: Boolean = false): Seq[(ScType, PsiNamedElement, Set[ImportUsed])] = {
    val processor = new CollectImplicitsProcessor

    // Collect implicit conversions from bottom to up
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      if (place == null) return
      if (!place.processDeclarations(processor,
        ResolveState.initial,
        lastParent, this)) return
      if (!processor.changedLevel) return
      treeWalkUp(place.getContext, place)
    }
    treeWalkUp(this, null)

    val typez: ScType =
      if (!fromUnder) getTypeWithoutImplicits(TypingContext.empty).getOrElse(return Seq.empty)
      else getTypeWithoutImplicitsWithoutUnderscore(TypingContext.empty).getOrElse(return Seq.empty)

    val expandedType: ScType = exp match {
      case Some(expected) => new ScFunctionType(expected, Seq(typez))(getProject, getResolveScope)
      case None => typez
    }
    for (obj <- ScalaPsiUtil.collectImplicitObjects(expandedType, this)) {
      obj.processDeclarations(processor, ResolveState.initial, null, this)
    }

    val result = new ArrayBuffer[(ScType, PsiNamedElement, Set[ImportUsed])]
    if (typez == Nothing) return result.toSeq
    if (typez.isInstanceOf[ScUndefinedType]) return result.toSeq
    
    val sigsFound = processor.candidatesS.map((r: ScalaResolveResult) => {
      if (!PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(r.element), this, false)) { //to prevent infinite recursion
        ProgressManager.checkCanceled
        lazy val funType: ScParameterizedType = {
          val fun = "scala.Function1"
          val funClass = JavaPsiFacade.getInstance(this.getProject).findClass(fun, this.getResolveScope)
          funClass match {
            case cl: ScTrait => new ScParameterizedType(ScType.designator(funClass), cl.typeParameters.map(tp =>
              new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty), 1)))
          }
        }
        val subst = r.substitutor
        val (tp: ScType, retTp: ScType) = r.element match {
          case f: ScFunction if f.paramClauses.clauses.length > 0 => {
            val params = f.paramClauses.clauses.apply(0).parameters
            (subst.subst(params.apply(0).getType(TypingContext.empty).getOrElse(Nothing)),
             subst.subst(f.returnType.getOrElse(Nothing)))
          }
          case f: ScFunction => {
            Conformance.undefinedSubst(funType, subst.subst(f.returnType.get)).getSubstitutor match {
              case Some(subst) => (subst.subst(funType.typeArgs.apply(0)), subst.subst(funType.typeArgs.apply(1)))
              case _ => (Nothing, Nothing)
            }
          }
          case b: ScBindingPattern => {
            Conformance.undefinedSubst(funType, subst.subst(b.getType(TypingContext.empty).get)).getSubstitutor match {
              case Some(subst) => (subst.subst(funType.typeArgs.apply(0)), subst.subst(funType.typeArgs.apply(1)))
              case _ => (Nothing, Nothing)
            }
          }
          case param: ScParameter => {
            // View Bounds and Context Bounds are processed as parameters.
            Conformance.undefinedSubst(funType, subst.subst(param.getType(TypingContext.empty).get)).
                    getSubstitutor match {
              case Some(subst) => (subst.subst(funType.typeArgs.apply(0)), subst.subst(funType.typeArgs.apply(1)))
              case _ => (Nothing, Nothing)
            }
          }
          case obj: ScObject => {
            Conformance.undefinedSubst(funType, subst.subst(obj.getType(TypingContext.empty).get)).
                    getSubstitutor match {
              case Some(subst) => (subst.subst(funType.typeArgs.apply(0)), subst.subst(funType.typeArgs.apply(1)))
              case _ => (Nothing, Nothing)
            }
          }
        }
        val newSubst = r.element match {
          case f: ScFunction => inferMethodTypesArgs(f, r.substitutor)
          case _ => ScSubstitutor.empty
        }
        if (!typez.weakConforms(newSubst.subst(tp))) {
          (false, r, tp, retTp)
        } else {
          r.element match {
            case f: ScFunction if f.hasTypeParameters => {
              var uSubst = Conformance.undefinedSubst(newSubst.subst(tp), typez)
              for (tParam <- f.typeParameters) {
                val lowerType: ScType = tParam.lowerBound.getOrElse(Nothing)
                if (lowerType != Nothing) uSubst = uSubst.addLower((tParam.getName, ScalaPsiUtil.getPsiElementId(tParam)),
                  subst.subst(lowerType))
                val upperType: ScType = tParam.upperBound.getOrElse(Any)
                if (upperType != Any) uSubst = uSubst.addUpper((tParam.getName, ScalaPsiUtil.getPsiElementId(tParam)),
                  subst.subst(upperType))
              }

              //todo: pass implicit parameters

              uSubst.getSubstitutor match {
                case Some(substitutor) =>
                  exp match {
                    case Some(expected) =>
                      val additionalUSubst = Conformance.undefinedSubst(expected, newSubst.subst(retTp))
                      (uSubst + additionalUSubst).getSubstitutor match {
                        case Some(substitutor) =>
                          (true, r.copy(subst = substitutor.followed(r.substitutor)), substitutor.subst(tp), substitutor.subst(retTp))
                        case None =>
                          (true, r.copy(subst = substitutor.followed(r.substitutor)), substitutor.subst(tp), substitutor.subst(retTp))
                      }
                    case None =>
                      (true, r.copy(subst = substitutor.followed(r.substitutor)), substitutor.subst(tp), substitutor.subst(retTp))
                  }
                case _ => (false, r, tp, retTp)
              }
            }
            case _ => (true, r, tp, retTp)
          }
        }
      } else {
        (false, r, null: ScType, null: ScType)
      }
    })


    for ((pass, resolveResult, tp, rt) <- sigsFound if pass) {
      result += Tuple(rt, resolveResult.element, resolveResult.importsUsed)
    }

    result.toSeq
  }


  import ResolveTargets._
  private class CollectImplicitsProcessor extends BaseProcessor(Set(METHOD, VAL, VAR, OBJECT)) {

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      val subst: ScSubstitutor = getSubst(state)
      val funType: ScParameterizedType = {
        val funClasses =
          CommonClassesSearcher.getCachedClass(element.getManager, element.getResolveScope, "scala.Function1")
        val funClass: PsiClass = if (funClasses.length >= 1) funClasses(0) else null
        funClass match {
          case cl: ScTrait => new ScParameterizedType(ScType.designator(funClass), cl.typeParameters.map(tp =>
            new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty))))
          case _ => return true
        }
      }

      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          //there is special case for Predef.conforms method
          case f: ScFunction if f.hasModifierProperty("implicit") && !isConformsMethod(f)=> {
            val clauses = f.paramClauses.clauses
            val followed = subst.followed(inferMethodTypesArgs(f, subst))
            //filtered cases
            if (clauses.length > 2 || (clauses.length == 2 && !clauses(1).isImplicit)) return true
            if (clauses.length == 0) {
              val rt = subst.subst(f.returnType.getOrElse(return true))
              if (!rt.conforms(funType)) return true
            } else if (clauses(0).parameters.length != 1 || clauses(0).isImplicit) return true
            candidatesSet += new ScalaResolveResult(f, subst, getImports(state))
          }
          case b: ScBindingPattern => {
            ScalaPsiUtil.nameContext(b) match {
              case d: ScDeclaredElementsHolder if (d.isInstanceOf[ScValue] || d.isInstanceOf[ScVariable]) &&
                      d.asInstanceOf[ScModifierListOwner].hasModifierProperty("implicit") => {
                val tp = subst.subst(b.getType(TypingContext.empty).getOrElse(return true))
                if (!tp.conforms(funType)) return true
                candidatesSet += new ScalaResolveResult(b, subst, getImports(state))
              }
              case _ => return true
            }
          }
          case param: ScParameter => {
            val tp = subst.subst(param.getType(TypingContext.empty).getOrElse(return true))
            if (!tp.conforms(funType)) return true
            candidatesSet += new ScalaResolveResult(param, subst, getImports(state))
          }
          case obj: ScObject => {
            val tp = subst.subst(obj.getType(TypingContext.empty).getOrElse(return true))
            if (!tp.conforms(funType)) return true
            candidatesSet += new ScalaResolveResult(obj, subst, getImports(state))
          }
          case _ =>
        }
        case _ =>
      }
      true
    }
  }

  private def isConformsMethod(f: ScFunction): Boolean = {
    f.name == "conforms" && Option(f.getContainingClass).flatMap(cls => Option(cls.getQualifiedName)).exists(_ == "scala.Predef")
  }

  /**
   * Pick all type parameters by method maps them to the appropriate type arguments.
   */
  def inferMethodTypesArgs(fun: ScFunction, classSubst: ScSubstitutor) = {
    fun.typeParameters.foldLeft(ScSubstitutor.empty) {
      (subst, tp) => subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)),
        ScUndefinedType(new ScTypeParameterType(tp: ScTypeParam, classSubst)))
    }
  }
}

object ScImplicitlyConvertible {
  val IMPLICIT_RESOLUTION_KEY: Key[PsiClass] = Key.create("implicit.resolution.key")
  val IMPLICIT_CONVERSIONS_KEY: Key[CachedValue[collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]]] = Key.create("implicit.conversions.key")

  case class Implicit(tp: ScType, fun: ScTypedDefinition, importsUsed: Set[ImportUsed])
}
