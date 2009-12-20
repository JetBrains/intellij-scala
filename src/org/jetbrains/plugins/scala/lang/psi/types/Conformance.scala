package org.jetbrains.plugins.scala
package lang
package psi
package types

import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import nonvalue.{ScTypePolymorphicType, NonValueType, ScMethodType}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.Misc._
import api.statements._
import params._
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._
import util.PsiModificationTracker
import collection.Seq
import collection.mutable.{MultiMap, HashMap}
import lang.resolve.ScalaResolveResult

object Conformance {

  /**
   * Checks, whether the following assignment is correct:
   * val x: l = (y: r) 
   */
  def conforms(l: ScType, r: ScType): Boolean = conforms(l, r, HashSet.empty, new ScUndefinedSubstitutor)._1

  def undefinedSubst(l: ScType, r: ScType): ScUndefinedSubstitutor = conforms(l, r, HashSet.empty, new ScUndefinedSubstitutor)._2

  def conformsSeq(ls: Seq[ScType], rs: Seq[ScType]): Boolean = ls.length == rs.length && ls.zip(rs).foldLeft(true)((z, p) => z && conforms(p._1, p._2))

  private def conforms(l: ScType, r: ScType, visited: Set[PsiClass], subst: ScUndefinedSubstitutor, noBaseTypes: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled

    var undefinedSubst: ScUndefinedSubstitutor = subst

    (l, r) match {
      case (u1: ScUndefinedType, u2: ScUndefinedType) if u2.level > u1.level => return (true, undefinedSubst.addUpper(u2.tpt.name, u1))  //todo: remove
      case (u1: ScUndefinedType, u2: ScUndefinedType) if u2.level == u1.level => return (true, undefinedSubst)
      case (u: ScUndefinedType, tp: ScType) => return (true, undefinedSubst.addLower(u.tpt.name, tp))
      case (tp: ScType, u: ScUndefinedType) => return (true, undefinedSubst.addUpper(u.tpt.name, tp))
      case _ if l equiv r => return (true, undefinedSubst)
      case (ScMethodType(returnType1, params1, _), ScMethodType(returnType2, params2, _)) => {
        if (params1.length != params2.length) return (false, undefinedSubst)
        val t = conforms(returnType1, returnType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        var i = 0
        while (i < params1.length) {
          if (params1(i).isRepeated != params2(i).isRepeated) return (false, undefinedSubst)
          if (!params1(i).paramType.equiv(params2(i).paramType)) return (false, undefinedSubst)
          i = i + 1
        }
      }
      case (ScTypePolymorphicType(internalType1, typeParameters1), ScTypePolymorphicType(internalType2, typeParameters2)) => {
        if (typeParameters1.length != typeParameters2.length) return (false, undefinedSubst)
        var i = 0
        while (i < typeParameters1.length) {
          var t = conforms(typeParameters1(i).lowerType, typeParameters2(i).lowerType, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          t = conforms(typeParameters2(i).upperType, typeParameters1(i).lowerType, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        import Suspension._
        val subst = new ScSubstitutor(new collection.immutable.HashMap[String, ScType] ++ typeParameters1.zip(typeParameters2).map({
          tuple => (tuple._1.name, new ScTypeParameterType(tuple._2.name, List.empty, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
        }), Map.empty, Map.empty)
        val t = conforms(subst.subst(internalType1), internalType2, HashSet.empty, undefinedSubst)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        (true, undefinedSubst)
      }
      //todo: ScTypeConstructorType
      case (_: NonValueType, _) => return (false, undefinedSubst)
      case (_, _: NonValueType) => return (false, undefinedSubst)
      case (Any, _) => return (true, undefinedSubst)
      case (_, Nothing) => return (true, undefinedSubst)
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
            return (!conforms(notNullType, l), undefinedSubst) //todo: think about undefinedSubst
          }
          case _ => return (true, undefinedSubst)
        }
      }
      case (tpt: ScTypeParameterType, _) => return conforms(tpt.lower.v, r, HashSet.empty, undefinedSubst)
      case (_, tpt: ScTypeParameterType) => return conforms(l, tpt.upper.v, HashSet.empty, undefinedSubst)
      case (Null, _) => return (r == Nothing, undefinedSubst)
      case (AnyRef, _: ValType) => return (false, undefinedSubst)
      case (AnyRef, _) => return (true, undefinedSubst)
      case (Singleton, _: ScSingletonType) => return (true, undefinedSubst)
      case (Singleton, _) => return (false, undefinedSubst)
      case (AnyVal, _: ValType) => return (true, undefinedSubst)
      case (AnyVal, _) => return (false, undefinedSubst)
      case (ScTupleType(comps1: Seq[ScType]), ScTupleType(comps2: Seq[ScType])) => {
        if (comps1.length != comps2.length) return (false, undefinedSubst)
        var i = 0
        while (i < comps1.length) {
          val comp1 = comps1(i)
          val comp2 = comps2(i)
          val t = conforms(comp1, comp2, HashSet.empty, undefinedSubst)
          if (!t._1) return (false, undefinedSubst)
          else undefinedSubst = t._2
          i = i + 1
        }
        return (true, undefinedSubst)
      }
      case (fun: ScFunctionType, _) => {
        fun.resolveFunctionTrait match {
          case Some(tp) => return conforms(tp, r, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (_, fun: ScFunctionType) => {
        fun.resolveFunctionTrait match {
          case Some(tp) => return conforms(l, tp, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (tuple: ScTupleType, _) => {
        tuple.resolveTupleTrait match {
          case Some(tp) => return conforms(tp, r, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (_, tuple: ScTupleType) => {
        tuple.resolveTupleTrait match {
          case Some(tp) => return conforms(l, tp, visited, subst, noBaseTypes)
          case _ => return (false, undefinedSubst)
        }
      }
      case (ScParameterizedType(owner: ScUndefinedType, args1), ScParameterizedType(owner1: ScType, args2)) => {
        return (true, undefinedSubst.addLower(owner.tpt.name, r))
      }
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScUndefinedType, args2)) => {
        return (true, undefinedSubst.addUpper(owner1.tpt.name, l))
      }
      case (ScParameterizedType(owner: ScType, args1), ScParameterizedType(owner1: ScType, args2))
        if owner.equiv(owner1) => {
        if (args1.length != args2.length) return (false, undefinedSubst)
        ScType.extractClassType(owner) match {
          case Some((owner: PsiClass, _)) => {
            owner.getTypeParameters.zip(args1 zip args2) forall {
              case (tp, argsPair) => tp match {
                case scp: ScTypeParam if (scp.isContravariant) => {
                  val y = Conformance.conforms(argsPair._2, argsPair._1, HashSet.empty, undefinedSubst)
                  if (!y._1) return (false, undefinedSubst)
                  else undefinedSubst = y._2
                }
                case scp: ScTypeParam if (scp.isCovariant) => {
                  val y = Conformance.conforms(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                  if (!y._1) return (false, undefinedSubst)
                  else undefinedSubst = y._2
                }
                //this case filter out such cases like undefined type
                case _ => {
                  argsPair match {
                    case (u: ScUndefinedType, rt) => {
                      undefinedSubst = undefinedSubst.addLower(u.tpt.name, rt)
                      undefinedSubst = undefinedSubst.addUpper(u.tpt.name, rt)
                    }
                    case (lt, u: ScUndefinedType) => {
                      undefinedSubst = undefinedSubst.addLower(u.tpt.name, lt)
                      undefinedSubst = undefinedSubst.addUpper(u.tpt.name, lt)
                    }
                    case (_: ScExistentialArgument, _) => {
                      val y = Conformance.conforms(argsPair._1, argsPair._2, HashSet.empty, undefinedSubst)
                      if (!y._1) return (false, undefinedSubst)
                      else undefinedSubst = y._2
                    }
                    case _ => if (!argsPair._1.equiv(argsPair._2)) return (false, undefinedSubst)
                  }
                }
              }
              true
            }
            return (true, undefinedSubst)
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
        val iterator = comps.iterator
        while (iterator.hasNext) {
          val comp = iterator.next
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
    if (noBaseTypes) return (false, undefinedSubst)
    ScType.extractClassType(r) match {
      case Some((clazz: PsiClass, _)) if visited.contains(clazz) => return (false, undefinedSubst)
      case Some((rClass: PsiClass, subst: ScSubstitutor)) => {
        ScType.extractClassType(l) match {
          case Some((lClass: PsiClass, _)) => {
            val inh = smartIsInheritor(rClass, lClass)
            if (!inh._1) return (false, undefinedSubst)
            val tp = subst.subst(inh._2)
            val t = conforms(l, tp, visited + rClass, undefinedSubst, true)
            if (t._1) return (true, t._2)
            else return (false, undefinedSubst)
          }
          case _ => return (false, undefinedSubst)
        }
      }
      case _ => {
        var bases: Seq[ScType] = BaseTypes.get(r)
        val iterator = bases.iterator
        while (iterator.hasNext) {
          val tp = iterator.next
          val t = conforms(l, tp, visited, undefinedSubst, true)
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

  private def smartIsInheritor(leftClass: PsiClass, rightClass: PsiClass): (Boolean, ScType) = {
    val bases: Seq[ScType] = leftClass match {
      case td: ScTypeDefinition => td.superTypes
      case _ => leftClass.getSuperTypes.map(ScType.create(_, leftClass.getProject)).toSeq
    }
    val iterator = bases.iterator
    var res: ScType = null
    while (iterator.hasNext) {
      val tp = iterator.next
      ScType.extractClassType(tp) match {
        case Some((clazz: PsiClass, subst)) if clazz == rightClass => {
          if (res == null) res = tp
          else if (tp.conforms(res)) res = tp
        }
        case Some((clazz: PsiClass, subst)) => {
          val recursive = smartIsInheritor(clazz, rightClass)
          if (recursive._1) {
            if (res == null) res = recursive._2
            else if (recursive._2.conforms(res)) res = recursive._2
          }
        }
        case _ =>
      }
    }
    if (res == null) (false, null)
    else (true, res)
  }
}