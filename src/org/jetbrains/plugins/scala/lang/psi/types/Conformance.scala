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
  def conforms(l: ScType, r: ScType): Boolean = conforms(l, r, HashSet.empty)

  def conformsSeq(ls: Seq[ScType], rs: Seq[ScType]): Boolean = ls.length == rs.length && ls.zip(rs).foldLeft(true)((z, p) => z && conforms(p._1, p._2, HashSet.empty))

  private def conforms(l: ScType, r: ScType, visited: Set[PsiClass]): Boolean = {
    ProgressManager.checkCanceled

    (l, r) match {
      case _ if l equiv r => return true
      case (Any, _) => return true
      case (_, Nothing) => return true
      case (Unit, _) => return true
      /*
        this case for checking: val x: T = null
        This is good if T class type: T <: AnyRef and !(T <: NotNull)
       */
      case (_, Null) => {
        if (!conforms(AnyRef, l)) return false
        ScType.extractDesignated(l) match {
          case Some((el, _)) => {
            val notNullClass = JavaPsiFacade.getInstance(el.getProject).findClass("scala.NotNull")
            val notNullType = ScDesignatorType(notNullClass)
            return !conforms(l, notNullType)
          }
          case _ => return true
        }
      }
      case (tpt: ScTypeParameterType, _) => return true //todo: conforms(tpt.lower.v, r)  it's possible after Undefined type creation
      case (_, tpt: ScTypeParameterType) => return true //todo: conforms(l, tpt.upper.v)
      case (Null, _) => return r == Nothing
      case (AnyRef, _: ValType) => return false
      case (AnyRef, _) => return true
      case (Singleton, _: ScSingletonType) => return true
      case (Singleton, _) => return false
      case (AnyVal, _: ValType) => return true
      case (AnyVal, _) => return false
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScType, args2))
        if owner equiv owner1 => {
        if (args1.length != args2.length) return false
        ScType.extractClassType(owner) match {
          case Some((owner: PsiClass, _)) => {
            owner.getTypeParameters.zip(args1 zip args2) forall {
              case (tp, argsPair) => tp match {
                case scp: ScTypeParam if (scp.isCovariant) => if (!argsPair._1.conforms(argsPair._2)) return false
                case scp: ScTypeParam if (scp.isContravariant) => if (!argsPair._2.conforms(argsPair._1)) return false
                //this case filter out such cases like undefined type
                case _ => if (!argsPair._1.isInstanceOf[ScTypeParameterType] &&
                              !argsPair._2.isInstanceOf[ScTypeParameterType]) argsPair._1 match {
                  case _: ScExistentialArgument => if (!argsPair._2.conforms(argsPair._1)) return false
                  case _ => if (!argsPair._1.equiv(argsPair._2)) return false
                }
              }
              return true
            }
          }
          case _ => return false
        }
      }
      case (c@ScCompoundType(comps, decls, types), _) => {
        return comps.forall(_ conforms r) && (ScType.extractClassType(r) match {
          case Some((clazz, subst)) => {
            if (!decls.isEmpty || (comps.isEmpty && decls.isEmpty && types.isEmpty)) { //if decls not empty or it's synthetic created
              val sigs = getSignatureMap(clazz)
              for ((sig, t) <- c.signatureMap) {
                sigs.get(sig) match {
                  case None => return false
                  case Some(t1) => if (!subst.subst(t1).conforms(t)) return false
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
                        if (!s.subst(ta.upperBound.getOrElse(Any)).conforms(t.upperBound.getOrElse(Any)) ||
                                !t.lowerBound.getOrElse(Nothing).conforms(s.subst(ta.lowerBound.getOrElse(Nothing)))) return false
                      }
                      case inner: PsiClass => {
                        val des = ScParameterizedType.create(inner, subst1 followed subst)
                        if (!subst.subst(des).conforms(t.upperBound.getOrElse(Any)) || !t.lowerBound.getOrElse(Nothing).conforms(des)) return false
                      }
                    }
                  }
                }
              }
            }
            true
          }
          case None => r match {
            case c1@ScCompoundType(comps1, _, _) => comps1.forall(c conforms _) && (
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
                                  case Some(rt) => rt1.conforms(subst.subst(rt))
                                }
                              }
                            }
                          }
                          case Some(rt) => rt1.conforms(rt)
                        }
                        //todo check for refinement's type decls
                      }
                    })
            case _ => false
          }
        })
      }

      case (ScSkolemizedType(_, _, lower, _), _) => return conforms(lower, r)
      case (ScPolymorphicType(_, _, lower, _), _) => return conforms(lower.v, r) //todo implement me
      case (ScExistentialArgument(_, params, lower, upper), _) if params.isEmpty => return conforms(upper, r)
      case (ex@ScExistentialType(q, wilds), _) => return conforms(ex.substitutor.subst(q), r)
      case (_, s: ScSingletonType) => {
        ScType.extractClassType(l) match {
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Singleton" => return true
          case _ => if (conforms(l, s.pathType)) return true
        }
      }
      case (_, ScSkolemizedType(_, _, _, upper)) => return conforms(l, upper)
      case (_, ScCompoundType(comps, _, _)) => return comps.find(conforms(l, _))
      case (_, ScExistentialArgument(_, params, _, upper)) if params.isEmpty => return conforms(l, upper)
      case (_, ex: ScExistentialType) => return conforms(l, ex.skolem)
      case (_, proj: ScProjectionType) => {
        proj.element match {
          case Some(syntheticClass: ScSyntheticClass) => return conforms(l, syntheticClass.t)
          case _ =>
        }
      }
      case (_, ScPolymorphicType(_, _, _, upper)) => {
        val uBound = upper.v
        ScType.extractClassType(uBound) match {
          case Some((pc, _)) if visited.contains(pc) => return conforms(l, ScDesignatorType(pc), visited + pc)
          case Some((pc, _)) => return conforms(l, uBound, visited + pc)
          case None => return conforms(l, uBound, visited)
        }
      }
      case _ =>
    }
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return false
      case Some((clazz: PsiClass, _)) => {
        return BaseTypes.get(r).find {t => conforms(l, t, visited + clazz)}
      }
      case _ => return BaseTypes.get(r).find {t => conforms(l, t, visited)}
    }
    return true
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