package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor.LazyDepMethodTypes
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.annotation.tailrec
import scala.language.implicitConversions

/**
* @author ven
*/
object ScSubstitutor {
  val empty: ScSubstitutor = new ScSubstitutor(Map.empty, Map.empty, None) {
    override def toString: String = "Empty substitutor"

    override def subst(t: ScType): ScType = t
    override def substInternal(t: ScType): ScType = t
  }

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")

  private val followLimit = 800

  var cacheSubstitutions = false

  val cache: scala.collection.mutable.Map[(String, Long), ScType] = scala.collection.mutable.Map()

  def apply(updateThisType: ScType): ScSubstitutor = {
    new ScSubstitutor(Map.empty, Map.empty, Some(updateThisType))
  }

  def apply(tvMap: Map[(String, Long), ScType],
            aliasesMap: Map[String, Suspension] = Map.empty,
            updateThisType: Option[ScType] = None,
            follower: Option[ScSubstitutor] = None,
            depMethodTypes: Option[LazyDepMethodTypes] = None): ScSubstitutor = {
    new ScSubstitutor(tvMap, aliasesMap, updateThisType, follower, depMethodTypes)
  }

  def apply(dependentMethodTypes: () => Map[Parameter, ScType]): ScSubstitutor = {
    new ScSubstitutor(Map.empty, Map.empty, None, None, Some(dependentMethodTypes))
  }

  class LazyDepMethodTypes(private var fun: () => Map[Parameter, ScType]) {
    lazy val value: Map[Parameter, ScType] = {
      val res = fun()
      fun = null
      res
    }
  }

  object LazyDepMethodTypes {
    implicit def toValue(lz: LazyDepMethodTypes): Map[Parameter, ScType] = lz.value
    implicit def toLazy(fun: () => Map[Parameter, ScType]): LazyDepMethodTypes = new LazyDepMethodTypes(fun)
  }
}

class ScSubstitutor private (val tvMap: Map[(String, Long), ScType],
                             val aliasesMap: Map[String, Suspension],
                             val updateThisType: Option[ScType],
                             val follower: Option[ScSubstitutor] = None,
                             val depMethodTypes: Option[LazyDepMethodTypes] = None) {

  override def toString: String = {
    val followerText = if (follower.nonEmpty) " >> " + follower.get.toString else ""
    s"ScSubstitutor($tvMap, $aliasesMap, $updateThisType)$followerText"
  }

  def bindT(name : (String, Long), t: ScType): ScSubstitutor = {
    ScSubstitutor(tvMap + ((name, t)), aliasesMap, updateThisType, follower, depMethodTypes)
  }

  def bindA(name: String, f: () => ScType): ScSubstitutor = {
    ScSubstitutor(tvMap, aliasesMap + ((name, Suspension(f))), updateThisType, follower, depMethodTypes)
  }

  def putAliases(template: ScTemplateDefinition): ScSubstitutor = {
    var result = this
    for (alias <- template.aliases) {
      alias match {
        case aliasDef: ScTypeAliasDefinition if aliasesMap.get(aliasDef.name).isEmpty =>
          result = result bindA(aliasDef.name, () => aliasDef.aliasedType(TypingContext.empty).getOrAny)
        case _ =>
      }
    }
    result
  }

  def followUpdateThisType(tp: ScType): ScSubstitutor = {
    tp match {
      case ScThisType(template) =>
        var zSubst = ScSubstitutor(ScThisType(template))
        var placer = template.getContext
        while (placer != null) {
          placer match {
            case t: ScTemplateDefinition => zSubst = zSubst.followed(
              ScSubstitutor(ScThisType(t))
            )
            case _ =>
          }
          placer = placer.getContext
        }
        zSubst.followed(this)
      case _ => ScSubstitutor(tp).followed(this)
    }
  }
  def followed(s: ScSubstitutor): ScSubstitutor = followed(s, 0)

  def isUpdateThisSubst: Option[ScType] = {
    if (tvMap.size + aliasesMap.size == 0 && depMethodTypes.isEmpty) updateThisType
    else None
  }

  private def followed(s: ScSubstitutor, level: Int): ScSubstitutor = {
    @tailrec
    def hasFollower(th: ScSubstitutor, s: ScSubstitutor): Boolean = th.follower match {
      case None => false
      case Some(f) => (f eq s) || hasFollower(f, s)
    }

    if (level > ScSubstitutor.followLimit)
      throw new RuntimeException("Too much followers for substitutor: " + this.toString)

    val trivialOrRecursive = s == null || s.isEmpty || (s eq this) || (level == 0 && hasFollower(this, s))

    if (trivialOrRecursive) this
    else if (this.isEmpty) s
    else {
      val newFollower = Option(if (follower.nonEmpty) follower.get.followed(s, level + 1) else s)
      ScSubstitutor(tvMap, aliasesMap, updateThisType, newFollower, depMethodTypes)
    }
  }

  private def isEmpty: Boolean = (this eq ScSubstitutor.empty) || this == ScSubstitutor.empty

  def subst(t: ScType): ScType = try {
    if (ScSubstitutor.cacheSubstitutions) ScSubstitutor.cache ++= this.tvMap
    if (follower.nonEmpty) follower.get.subst(substInternal(t)) else substInternal(t)
  } catch {
    case s: StackOverflowError =>
      throw new RuntimeException("StackOverFlow during ScSubstitutor.subst(" + t + ") this = " + this, s)
  }

  private def extractTpt(tpt: TypeParameterType, t: ScType): ScType = {
    if (tpt.arguments.isEmpty) t
    else t match {
      case ParameterizedType(designator, _) => designator
      case _ => t
    }
  }

  protected def substInternal(t: ScType) : ScType = {
    import t.projectContext

    var result: ScType = t
    val visitor = new ScalaTypeVisitor {
      override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = {
        val ScTypePolymorphicType(internalType, typeParameters) = t
        result = ScTypePolymorphicType(substInternal(internalType),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(
                parameters, // todo: is it important here to update?
                Suspension(substInternal(lowerType.v)),
                Suspension(substInternal(upperType.v)),
                psiTypeParameter)
          })
      }

      override def visitAbstractType(a: ScAbstractType): Unit = {
        val parameterType = a.parameterType
        result = tvMap.get(parameterType.nameAndId) match {
          case None => a
          case Some(v) => v match {
            case tpt: TypeParameterType if tpt.psiTypeParameter == parameterType.psiTypeParameter => a
            case _ => extractTpt(parameterType, v)
          }
        }
      }

      override def visitMethodType(m: ScMethodType): Unit = {
        val ScMethodType(retType, params, isImplicit) = m
        implicit val elementScope = m.elementScope
        result = ScMethodType(substInternal(retType),
          params.map(p => p.copy(paramType = substInternal(p.paramType),
            expectedType = substInternal(p.expectedType), defaultType = p.defaultType.map(substInternal))), isImplicit)
      }

      override def visitUndefinedType(u: UndefinedType): Unit = {
        val parameterType = u.parameterType
        result = tvMap.get(parameterType.nameAndId) match {
          case None => u
          case Some(v) => v match {
            case tpt: TypeParameterType if tpt.psiTypeParameter == parameterType.psiTypeParameter => u
            case _ => extractTpt(parameterType, v)
          }
        }
      }

      override def visitTypeParameterType(tpt: TypeParameterType): Unit = {
        result = tvMap.get(tpt.nameAndId) match {
          case None => tpt
          case Some(v) => extractTpt(tpt, v)
        }
      }

      override def visitDesignatorType(d: ScDesignatorType): Unit = {
        val depMethodType = depMethodTypes.flatMap(_.value.collectFirst {
          case (parameter: Parameter, tp: ScType) if parameter.paramInCode.contains(d.element) => tp
        })
        result = depMethodType.getOrElse(d)
      }

      override def visitThisType(th: ScThisType): Unit = {
        val clazz = th.element
        def hasRecursiveThisType(tp: ScType): Boolean = {
          var res = false
          tp.recursiveUpdate {
            case tpp if res => (true, tpp)
            case tpp@ScThisType(`clazz`) =>
              res = true
              (true, tpp)
            case tpp => (false, tpp)
          }
          res
        }
        result = updateThisType match {
          case Some(oldTp) if !hasRecursiveThisType(oldTp) => //todo: hack to avoid infinite recursion during type substitution
            var tp = oldTp
            def update(typez: ScType): ScType = {
              typez.extractDesignated(expandAliases = true) match {
                case Some(t: ScTypeDefinition) =>
                  if (t == clazz) tp
                  else if (isInheritorDeep(t, clazz)) tp
                  else {
                    t.selfType match {
                      case Some(selfType) =>
                        selfType.extractDesignated(expandAliases = true) match {
                          case Some(cl: PsiClass) =>
                            if (cl == clazz) tp
                            else if (isInheritorDeep(cl, clazz)) tp
                            else null
                          case _ =>
                            selfType match {
                              case ScCompoundType(types, _, _) =>
                                val iter = types.iterator
                                while (iter.hasNext) {
                                  val tps = iter.next()
                                  tps.extractClass match {
                                    case Some(cl) =>
                                      if (cl == clazz) return tp
                                    case _ =>
                                  }
                                }
                              case _ =>
                            }
                            null
                        }
                      case None => null
                    }
                  }
                case Some(cl: PsiClass) =>
                  typez match {
                    case t: TypeParameterType => return update(t.upperType.v)
                    case p@ParameterizedType(_, _) =>
                      p.designator match {
                        case TypeParameterType(_, _, upper, _) => return update(p.substitutor.subst(upper.v))
                        case _ =>
                      }
                    case _ =>
                  }
                  if (cl == clazz) tp
                  else if (isInheritorDeep(cl, clazz)) tp
                  else null
                case Some(named: ScTypedDefinition) =>
                  update(named.getType(TypingContext.empty).getOrAny)
                case _ =>
                  typez match {
                    case ScCompoundType(types, _, _) =>
                      val iter = types.iterator
                      while (iter.hasNext) {
                        val tps = iter.next()
                        tps.extractClass match {
                          case Some(cl) =>
                            if (cl == clazz) return tp
                            else if (isInheritorDeep(cl, clazz)) return tp
                          case _ =>
                        }
                      }
                    case t: TypeParameterType => return update(t.upperType.v)
                    case p@ParameterizedType(_, _) =>
                      p.designator match {
                        case TypeParameterType(_, _, upper, _) => return update(p.substitutor.subst(upper.v))
                        case _ =>
                      }
                    case _ =>
                  }
                  null
              }
            }
            while (tp != null) {
              val up = update(tp)
              if (up != null) {
                result = up
                return
              }
              tp match {
                case ScThisType(template) =>
                  val parentTemplate = template.containingClass
                  if (parentTemplate != null) tp = ScThisType(parentTemplate.asInstanceOf[ScTemplateDefinition])
                  else tp = null
                case ScProjectionType(newType, _, _) => tp = newType
                case ParameterizedType(ScProjectionType(newType, _, _), _) => tp = newType
                case _ => tp = null
              }
            }
            t
          case _ => t
        }
      }

      override def visitExistentialArgument(s: ScExistentialArgument): Unit = {
        val ScExistentialArgument(name, args, lower, upper) = s
        result = ScExistentialArgument(name, args.map(t => substInternal(t).asInstanceOf[TypeParameterType]),
          substInternal(lower), substInternal(upper))
      }

      override def visitExistentialType(ex: ScExistentialType): Unit = {
        val ScExistentialType(q, wildcards) = ex
        //remove bound names
        val trunc = aliasesMap -- ex.boundNames
        val substCopy = ScSubstitutor(tvMap, trunc, updateThisType, follower, depMethodTypes)
        result = new ScExistentialType(substCopy.substInternal(q),
          wildcards.map(ex => substInternal(ex).asInstanceOf[ScExistentialArgument]))
      }

      override def visitParameterizedType(pt: ParameterizedType): Unit = {
        val typeArgs = pt.typeArguments
        result = pt.designator match {
          case tpt: TypeParameterType =>
            tvMap.get(tpt.nameAndId) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (tpt.arguments.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs.map(substInternal))
                }
              case _ =>
                substInternal(tpt) match {
                  case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case u@UndefinedType(parameterType, _) =>
            tvMap.get(parameterType.nameAndId) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (parameterType.arguments.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs map substInternal)
                }
              case _ =>
                substInternal(u) match {
                  case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case a@ScAbstractType(parameterType, _, _) =>
            tvMap.get(parameterType.nameAndId) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (parameterType.arguments.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs map substInternal)
                }
              case _ =>
                substInternal(a) match {
                  case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case designator =>
            substInternal(designator) match {
              case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
              case des => ScParameterizedType(des, typeArgs map substInternal)
            }
        }
      }

      override def visitJavaArrayType(j: JavaArrayType): Unit = {
        result = JavaArrayType(substInternal(j.argument))
      }

      override def visitProjectionType(p: ScProjectionType): Unit = {
        val ScProjectionType(proj, element, s) = p
        val res = ScProjectionType(substInternal(proj), element, s)
        result = res match {
          case res: ScProjectionType if !s =>
            val actualElement = p.actualElement
            if (actualElement.isInstanceOf[ScTypeDefinition] &&
              actualElement != res.actualElement) ScProjectionType(res.projected, res.element, superReference = true)
            else res
          case _ => res
        }
      }

      override def visitCompoundType(comp: ScCompoundType): Unit = {
        val ScCompoundType(comps, signatureMap, typeMap) = comp
        def substTypeParam: TypeParameter => TypeParameter = {
          case TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) =>
            TypeParameter(
              typeParameters.map(substTypeParam),
              Suspension(substInternal(lowerType.v)),
              Suspension(substInternal(upperType.v)),
              psiTypeParameter)
        }
        val middleRes = ScCompoundType(comps.map(substInternal), signatureMap.map {
          case (s: Signature, tp: ScType) =>
            val pTypes: List[Seq[() => ScType]] = s.substitutedTypes.map(_.map(f => () => substInternal(f())))
            val tParams = s.typeParams.subst(substTypeParam)
            val rt: ScType = substInternal(tp)
            (new Signature(s.name, pTypes, s.paramLength, tParams,
              ScSubstitutor.empty, s.namedElement match {
                case fun: ScFunction =>
                  ScFunction.getCompoundCopy(pTypes.map(_.map(_()).toList), tParams.toList, rt, fun)
                case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
                case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
                case named => named
              }, s.hasRepeatedParam), rt)
        }, typeMap.map {
          case (s, sign) => (s, sign.updateTypes(substInternal))
        })
        //todo: this is ugly workaround for
        result = updateThisType match {
          case Some(thisType@ScDesignatorType(param: ScParameter)) =>
            val paramType = param.getRealParameterType(TypingContext.empty).getOrAny
            if (paramType.conforms(middleRes)) thisType
            else middleRes
          case _ => middleRes
        }
      }
    }
    t.visitType(visitor)
    result
  }
}