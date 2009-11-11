package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.scala.collection.mutable.HashMap
import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.Misc._
import api.statements._
import params._
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._
import util.PsiModificationTracker

object Conformance {

  /**
   * Checks, whether the following assignment is correct:
   * val x: l = (y: r) 
   */
  def conforms(l: ScType, r: ScType): Boolean = conforms(l, r, HashSet.empty, new ScUndefinedSubstitutor)._1

  def undefinedSubst(l: ScType, r: ScType): ScUndefinedSubstitutor = conforms(l, r, HashSet.empty, new ScUndefinedSubstitutor)._2

  def conformsSeq(ls: Seq[ScType], rs: Seq[ScType]): Boolean = ls.length == rs.length && ls.zip(rs).foldLeft(true)((z, p) => z && conforms(p._1, p._2))

  private def conforms(l: ScType, r: ScType, visited: Set[PsiClass], subst: ScUndefinedSubstitutor): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled

    var undefinedSubst: ScUndefinedSubstitutor = subst

    (l, r) match {
      case (u: ScUndefinedType, tp: ScType) => return (true, undefinedSubst.addUpper(u.tpt.name, tp))
      case (tp: ScType, u: ScUndefinedType) => return (true, undefinedSubst.addLower(u.tpt.name, tp))
      case _ if l equiv r => return (true, undefinedSubst)
      case (Any, _) => return (true, undefinedSubst)
      case (_, Nothing) => return (true, undefinedSubst)
      case (Unit, _) => return (true, undefinedSubst)
      /*
        this case for checking: val x: T = null
        This is good if T class type: T <: AnyRef and !(T <: NotNull)
       */
      case (_, Null) => {
        if (!conforms(AnyRef, l)) return (false, undefinedSubst)
        ScType.extractDesignated(l) match {
          case Some((el, _)) => {
            val notNullClass = JavaPsiFacade.getInstance(el.getProject).findClass("scala.NotNull")
            val notNullType = ScDesignatorType(notNullClass)
            return (!conforms(l, notNullType), undefinedSubst) //todo: think about undefinedSubst
          }
          case _ => return (true, undefinedSubst)
        }
      }
      case (tpt: ScTypeParameterType, _) => conforms(tpt.lower.v, r, HashSet.empty, undefinedSubst)
      case (_, tpt: ScTypeParameterType) => conforms(l, tpt.upper.v, HashSet.empty, undefinedSubst)
      case (Null, _) => return (r == Nothing, undefinedSubst)
      case (AnyRef, _: ValType) => return (false, undefinedSubst)
      case (AnyRef, _) => return (true, undefinedSubst)
      case (Singleton, _: ScSingletonType) => return (true, undefinedSubst)
      case (Singleton, _) => return (false, undefinedSubst)
      case (AnyVal, _: ValType) => return (true, undefinedSubst)
      case (AnyVal, _) => return (false, undefinedSubst)
      case (ScTupleType(comps1: Seq[ScType]), ScTupleType(comps2: Seq[ScType])) => {
        if (comps1.length != comps2.length) return (false, undefinedSubst)
        for ((comp1, comp2) <- comps1.zip(comps2)) {
          val t = conforms(comp1, comp2, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          else undefinedSubst = t._2
        }
        return (true, undefinedSubst)
      }
      case (ScFunctionType(retType1, params1), ScFunctionType(retType2, params2)) => {
        if (params1.length != params1.length) return (false, undefinedSubst)
        val t = conforms(retType1, retType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        else undefinedSubst = t._2
        for ((param1, param2) <- params1.zip(params2)) {
          val t = conforms(param2, param1, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          else undefinedSubst = t._2
        }
        return (true, undefinedSubst)
      }
      case (ScParameterizedType(owner: ScType, args), _) if (ScType.extractClassType(owner) match {
        case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == null => false
        case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Tuple") => true
        case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => true
        case _ => false
      }) => {
        ScType.extractClassType(owner) match {
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Tuple") => {
            return conforms(ScTupleType(args), r, visited, undefinedSubst)
          }
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => {
            return conforms(ScFunctionType(args(args.length - 1), args.slice(0, args.length - 1)), r, visited, undefinedSubst)
          }
        }
      }
      case (_, ScParameterizedType(owner: ScType, args)) if (ScType.extractClassType(owner) match {
        case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == null => false
        case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Tuple") => true
        case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => true
        case _ => false
      }) => {
        ScType.extractClassType(owner) match {
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Tuple") => {
            return conforms(l, ScTupleType(args), visited, undefinedSubst)
          }
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => {
            return conforms(l, ScFunctionType(args(args.length - 1), args.slice(0, args.length - 1)), visited, undefinedSubst)
          }
        }
      }
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScType, args2))
        if owner.equiv(owner1) || owner.isInstanceOf[ScUndefinedType] ||
                owner1.isInstanceOf[ScUndefinedType] => {
        if (args1.length != args2.length) return (false, undefinedSubst)
        ScType.extractClassType(owner) match {
          case Some((owner: PsiClass, _)) => {
            owner.getTypeParameters.zip(args1 zip args2) forall {
              case (tp, argsPair) => tp match {
                case scp: ScTypeParam if (scp.isContravariant) => if (!argsPair._1.conforms(argsPair._2)) return (false, undefinedSubst)
                case scp: ScTypeParam if (scp.isCovariant) => if (!argsPair._2.conforms(argsPair._1)) return (false, undefinedSubst)
                //this case filter out such cases like undefined type
                case _ => if (!argsPair._1.isInstanceOf[ScUndefinedType] &&
                              !argsPair._2.isInstanceOf[ScUndefinedType]) argsPair._1 match {
                  case _: ScExistentialArgument => if (!argsPair._2.conforms(argsPair._1)) return (false, undefinedSubst)
                  case _ => if (!argsPair._1.equiv(argsPair._2)) return (false, undefinedSubst)
                }
              }
              return (true, undefinedSubst)
            }
          }
          case _ => return (false, undefinedSubst)
        }
      }
      case (c@ScCompoundType(comps, decls, types), _) => {
        return (comps.forall(tp => {
          val t = conforms(r, tp, HashSet.empty, undefinedSubst)
          undefinedSubst = t._2
          t._1
        }) && (ScType.extractClassType(r) match {
          case Some((clazz, subst)) => {
            if (!decls.isEmpty || (comps.isEmpty && decls.isEmpty && types.isEmpty)) { //if decls not empty or it's synthetic created
              val sigs = getSignatureMap(clazz)
              for ((sig, t) <- c.signatureMap) {
                sigs.get(sig) match {
                  case None => return (false, undefinedSubst)
                  case Some(t1) => {
                    val tt = conforms(t, subst.subst(t1), HashSet.empty, undefinedSubst)
                    if (!tt._1) return (false, undefinedSubst)
                    else undefinedSubst = tt._2
                  }
                }
              }
            }
            if (!types.isEmpty) {
              val hisTypes = TypeDefinitionMembers.getTypes(clazz)
              for (t <- types) {
                hisTypes.get(t) match {
                  case None => false
                  case Some(n) => {
                    val subst1 = n.substitutor
                    n.info match {
                      case ta: ScTypeAlias => {
                        val s = subst1 followed subst
                        val tt = conforms(t.upperBound.getOrElse(Any), s.subst(ta.upperBound.getOrElse(Any)), HashSet.empty, undefinedSubst)
                        if (!tt._1) return (false, undefinedSubst)
                        else undefinedSubst = tt._2
                        val tt2 = conforms(s.subst(ta.lowerBound.getOrElse(Nothing)), t.lowerBound.getOrElse(Nothing), HashSet.empty, undefinedSubst)
                        if(!tt2._1) return (false, undefinedSubst)
                        else undefinedSubst = tt2._2
                      }
                      case inner: PsiClass => {
                        val des = ScParameterizedType.create(inner, subst1 followed subst)
                        val tt = conforms(t.upperBound.getOrElse(Any), subst.subst(des), HashSet.empty, undefinedSubst)
                        if (!tt._1) return (false, undefinedSubst)
                        else undefinedSubst = tt._2
                        val tt2 = conforms(des, t.lowerBound.getOrElse(Nothing), HashSet.empty, undefinedSubst)
                        if (!tt2._1) return (false, undefinedSubst)
                        else undefinedSubst = tt2._2
                      }
                    }
                  }
                }
              }
            }
            true
          }
          case None => r match {
            case c1@ScCompoundType(comps1, _, _) => comps1.forall(tp => {
              val t = conforms(tp, c, HashSet.empty, undefinedSubst)
              undefinedSubst = t._2
              t._1
            }) && (
                    c1.signatureMap.forall {
                      p => {
                        val s1 = p._1
                        val rt1 = p._2
                        c.signatureMap.get(s1) match {
                          case None => comps.find {
                            t => ScType.extractClassType(t) match {
                              case None => false
                              case Some((clazz, subst)) => {
                                val classSigs = getSignatureMap(clazz)
                                classSigs.get(s1) match {
                                  case None => false
                                  case Some(rt) => {
                                    val t = conforms(subst.subst(rt), rt1, HashSet.empty, undefinedSubst)
                                    undefinedSubst = t._2
                                    t._1
                                  }
                                }
                              }
                            }
                          }
                          case Some(rt) => {
                            val t = conforms(rt, rt1, HashSet.empty, undefinedSubst)
                            undefinedSubst = t._2
                            t._1
                          }
                        }
                        //todo check for refinement's type decls
                      }
                    })
            case _ => false
          }
        }), undefinedSubst)
      }

      case (ScSkolemizedType(_, _, lower, _), _) => return conforms(lower, r, HashSet.empty, undefinedSubst)
      case (ScPolymorphicType(_, _, lower, _), _) => return conforms(lower.v, r, HashSet.empty, undefinedSubst) //todo implement me
      case (ScExistentialArgument(_, params, lower, upper), _) if params.isEmpty => return conforms(upper, r, HashSet.empty, undefinedSubst)
      case (ex@ScExistentialType(q, wilds), _) => return conforms(ex.substitutor.subst(q), r, HashSet.empty, undefinedSubst)
      case (_, s: ScSingletonType) => {
        ScType.extractClassType(l) match {
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Singleton" => return (true, undefinedSubst)
          case _ => if (conforms(l, s.pathType)) return (true, undefinedSubst)
        }
      }
      case (_, ScSkolemizedType(_, _, _, upper)) => return conforms(l, upper, HashSet.empty, undefinedSubst)
      case (_, ScCompoundType(comps, _, _)) => {
        for (comp <- comps) {
          val t = conforms(l, comp, HashSet.empty, undefinedSubst)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
      }
      case (_, ScExistentialArgument(_, params, _, upper)) if params.isEmpty => return conforms(l, upper, HashSet.empty, undefinedSubst)
      case (_, ex: ScExistentialType) => return conforms(l, ex.skolem, HashSet.empty, undefinedSubst)
      case (_, proj: ScProjectionType) => {
        proj.element match {
          case Some(syntheticClass: ScSyntheticClass) => return conforms(l, syntheticClass.t, HashSet.empty, undefinedSubst)
          case _ =>
        }
      }
      case (_, ScPolymorphicType(_, _, _, upper)) => {
        val uBound = upper.v
        ScType.extractClassType(uBound) match {
          case Some((pc, _)) if visited.contains(pc) => return conforms(l, ScDesignatorType(pc), visited + pc, undefinedSubst)
          case Some((pc, _)) => return conforms(l, uBound, visited + pc, undefinedSubst)
          case None => return conforms(l, uBound, visited, undefinedSubst)
        }
      }
      case _ =>
    }
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return (false, undefinedSubst)
      case Some((clazz: PsiClass, _)) => {
        for (tp <- BaseTypes.get(r)) {
          val t = conforms(l, tp, visited + clazz, undefinedSubst)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
        //return .find {t => conforms(l, t, visited + clazz)}
      }
      case _ => {
        for (tp <- BaseTypes.get(r)) {
          val t = conforms(l, tp, visited, undefinedSubst)
          if (t._1) return (true, t._2)
        }
        return (false, undefinedSubst)
      }//return BaseTypes.get(r).find {t => conforms(l, t, visited)}
    }
  }

  def getSignatureMapInner(clazz: PsiClass): HashMap[Signature, ScType] = {
    val m = new HashMap[Signature, ScType]
    for ((full, _) <- TypeDefinitionMembers.getSignatures(clazz)) {
      m += ((full.sig, full.retType))
    }
    m
  }

  def getSignatureMap(clazz: PsiClass): HashMap[Signature, ScType] = {
    CachesUtil.get(
      clazz, CachesUtil.SIGNATURES_MAP_KEY,
      new CachesUtil.MyProvider(clazz, {clazz: PsiClass => getSignatureMapInner(clazz)})
        (PsiModificationTracker.MODIFICATION_COUNT)
      )
  }
}