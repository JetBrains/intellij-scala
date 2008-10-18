package org.jetbrains.plugins.scala.lang.psi.types

import scala.Misc._
import api.statements._
import params._
import resolve.ScalaResolveResult
import api.toplevel.typedef.ScTypeDefinition
import impl.toplevel.typedef.TypeDefinitionMembers
import _root_.scala.collection.immutable.HashSet

import com.intellij.psi._

object Conformance {
  def conforms (l : ScType, r : ScType) : Boolean = conforms(l, r, HashSet.empty)

  private def conforms (l : ScType, r : ScType, visited : Set[PsiClass]) : Boolean = {
    if (l equiv r) true
    else l match {
      case Any => true
      case Nothing => false
      case Null => r == Nothing
      case AnyRef => r match {
        case Null => true
        case _: ScParameterizedType => true
        case _: ScDesignatorType => true
        case _: ScSingletonType => true
        case _ => false
      }
      case Singleton => r match {
        case _: ScSingletonType => true
        case _ => false
      }
      case AnyVal => r match {
        case _: ValType => true
        case _ => false
      }

      case ScTypeAliasType(_, _, lower, _) => conforms(lower, r)
      case ScTypeVariable(_, _, lower, _) => conforms(lower, r)

      case ScParameterizedType(ScDesignatorType(owner : PsiClass), args1) => r match {
        case ScParameterizedType(ScDesignatorType(owner1 : PsiClass), args2) if (owner == owner1) =>
          owner.getTypeParameters.equalsWith(args1 zip args2) {
            (tp, argsPair) => tp match {
              case scp : ScTypeParam if (scp.isCovariant) => if (!argsPair._1.conforms(argsPair._2)) return false
              case scp : ScTypeParam if (scp.isContravariant) => if (!argsPair._2.conforms(argsPair._1)) return false
              case _ => argsPair._1 match {
                case _ : ScExistentialArgument => if (!argsPair._2.conforms(argsPair._1)) return false
                case _ => if (!argsPair._1.equiv(argsPair._2)) return false
              }
            }
            true
          }
        case _ => rightRec(l, r, visited)
      }

      case c@ScCompoundType(comps, decls, types) => comps.forall(_ conforms r) && (ScType.extractClassType(r) match {
        case Some((clazz, subst)) => {
          if (!decls.isEmpty) {
            val sigs = TypeDefinitionMembers.getSignatures(clazz)
            for ((sig, t) <- c.signatureMap) {
              sigs.get(sig) match {
                case None => return false
                case Some(retType) => if (!subst.subst(retType).conforms(t)) return false
              }
            }
          }
          if (!types.isEmpty) {
            val hisTypes = TypeDefinitionMembers.getTypes(clazz)
            for (t <- types) {
              hisTypes.get(t) match {
                case None => return false
                case Some(n) => {
                  val subst1 = n.substitutor
                  n.info match {
                    case ta: ScTypeAlias => {
                      val s = subst1 followed subst
                      if (!s.subst(ta.upperBound).conforms(t.upperBound) ||
                          !t.lowerBound.conforms(s.subst(ta.lowerBound))) return false
                    }
                    case inner: PsiClass => {
                      val des = ScParameterizedType.create(inner, subst1 followed subst)
                      if (!subst.subst(des).conforms(t.upperBound) || !t.lowerBound.conforms(des)) return false
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
             c1.signatureMap.forall {p => {
               val s1 = p._1
               val rt1 = p._2
               c.signatureMap.get(s1) match {
               case None => comps.find { t => ScType.extractClassType(t) match {
                   case None => false
                   case Some((clazz, subst)) => {
                     TypeDefinitionMembers.getSignatures(clazz).get(s1) match {
                       case None => false
                       case Some(rt) => rt1.conforms(subst.subst(rt))
                     }
                   }
                 }
               }
               case Some(rt) => rt1.conforms(rt)
             }
             //todo check for refinement's type decls
           }})
          case _ => false
        }
      })

      case ScExistentialArgument(_, Seq.empty, lower, _) => conforms(lower, r)
      case ex@ScExistentialType(q, wilds) => conforms(ex.substitutor.subst(q), r)

      case _ => rightRec(l, r, visited)
    }
  }

  private def rightRec(l: ScType, r: ScType, visited : Set[PsiClass]) : Boolean = r match {
    case sin : ScSingletonType => conforms(l, sin.pathType) 

    case ScDesignatorType(td: ScTypeDefinition) => td.superTypes.find {t => conforms(l, t, visited + td)}
    case ScDesignatorType(clazz: PsiClass) =>
    clazz.getSuperTypes.find {t => conforms(l, ScType.create(t, clazz.getProject), visited + clazz)}

    case proj : ScProjectionType => {
      proj.element match {
        case clazz : PsiClass => BaseTypes.get(proj).find{t => conforms(l, t, visited + clazz)}
        case _ => false
      }
    }

    case ScTypeAliasType(_, _, _, upper) => conforms(l, upper)
    case ScTypeVariable(_, _, _, upper) => conforms(l, upper)

    case p@ScParameterizedType(ScDesignatorType(td: ScTypeDefinition), _) => {
      val s = p.substitutor
      td.superTypes.find {t => conforms(l, s.subst(t), visited + td)}
    }
    case p@ScParameterizedType(ScDesignatorType(clazz: PsiClass), _) => {
      val s = p.substitutor
      clazz.getSuperTypes.find {t => conforms(l, s.subst(ScType.create(t, clazz.getProject)), visited + clazz)}
    }

    case ScCompoundType(comps, _, _) => comps.find(l conforms _)

    case ScExistentialArgument(_, Seq.empty, _, upper) => conforms(l, upper)

    case ex : ScExistentialType => conforms(l, ex.skolem)

    case _ => false //todo
  }
}