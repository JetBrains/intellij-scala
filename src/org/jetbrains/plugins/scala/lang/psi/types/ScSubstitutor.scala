package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.dotty.lang.psi.types.DottyTypeSystem
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
    ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension(f))), updateThisType, follower, depMethodTypes)
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
    if (level > ScSubstitutor.followLimit)
      throw new RuntimeException("Too much followers for substitutor: " + this.toString)
    if (this.isEmpty) s
    else if (s.isEmpty) this
    else {
      val newFollower = Option(if (follower.nonEmpty) follower.get followed(s, level + 1) else s)
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
    var result: ScType = t
    val visitor = new ScalaTypeVisitor {
      override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = {
        val ScTypePolymorphicType(internalType, typeParameters) = t
        result = ScTypePolymorphicType(substInternal(internalType),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(
                parameters, // todo: is it important here to update?
                new Suspension(substInternal(lowerType.v)),
                new Suspension(substInternal(upperType.v)),
                psiTypeParameter)
          })(t.typeSystem)
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
              typez.extractDesignated(withoutAliases = true) match {
                case Some((t: ScTypeDefinition, _)) =>
                  if (t == clazz) tp
                  else if (ScalaPsiUtil.cachedDeepIsInheritor(t, clazz)) tp
                  else {
                    t.selfType match {
                      case Some(selfType) =>
                        selfType.extractDesignated(withoutAliases = true) match {
                          case Some((cl: PsiClass, _)) =>
                            if (cl == clazz) tp
                            else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) tp
                            else null
                          case _ =>
                            selfType match {
                              case ScCompoundType(types, _, _) =>
                                val iter = types.iterator
                                while (iter.hasNext) {
                                  val tps = iter.next()
                                  tps.extractClass()(ScalaTypeSystem) match {
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
                case Some((cl: PsiClass, _)) =>
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
                  else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) tp
                  else null
                case Some((named: ScTypedDefinition, _)) =>
                  update(named.getType(TypingContext.empty).getOrAny)
                case _ =>
                  typez match {
                    case ScCompoundType(types, _, _) =>
                      val iter = types.iterator
                      while (iter.hasNext) {
                        val tps = iter.next()
                        tps.extractClass()(ScalaTypeSystem) match {
                          case Some(cl) =>
                            if (cl == clazz) return tp
                            else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) return tp
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
                  val parentTemplate = ScalaPsiUtil.getContextOfType(template, true, classOf[ScTemplateDefinition])
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
        result = JavaArrayType(substInternal(j.argument))(j.typeSystem)
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
              new Suspension(substInternal(lowerType.v)),
              new Suspension(substInternal(upperType.v)),
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
              }, s.hasRepeatedParam)(ScalaTypeSystem), rt)
        }, typeMap.map {
          case (s, sign) => (s, sign.updateTypes(substInternal))
        })
        //todo: this is ugly workaround for
        result = updateThisType match {
          case Some(thisType@ScDesignatorType(param: ScParameter)) =>
            val paramType = param.getRealParameterType(TypingContext.empty).getOrAny
            if (paramType.conforms(middleRes)(ScalaTypeSystem)) thisType
            else middleRes
          case _ => middleRes
        }
      }
    }
    t.visitType(visitor)
    result
  }
}

sealed trait ScUndefinedSubstitutor {

  type Name = (String, Long)

  case class SubstitutorWithBounds(subst: ScSubstitutor, lowers: Map[Name, ScType], upper: Map[Name, ScType])

  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Int = -1): ScUndefinedSubstitutor
  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Int = 1): ScUndefinedSubstitutor
  def getSubstitutor(notNonable: Boolean): Option[ScSubstitutor] = getSubstitutorWithBounds(notNonable).map(_._1)
  def getSubstitutor: Option[ScSubstitutor] = getSubstitutor(notNonable = false)
  def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor
  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor
  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)
  def isEmpty: Boolean
  def names: Set[Name]

  //subst, lowers, uppers
  def getSubstitutorWithBounds(notNonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])]
}

object ScUndefinedSubstitutor {
  def apply(upperMap: Map[(String, Long), Set[ScType]] = Map.empty,
            lowerMap: Map[(String, Long), Set[ScType]] = Map.empty,
            upperAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty,
            lowerAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty)
           (implicit typeSystem: TypeSystem): ScUndefinedSubstitutor = {

    new ScUndefinedSubstitutorImpl(upperMap, lowerMap, upperAdditionalMap, lowerAdditionalMap)(typeSystem)
  }

  def apply()(implicit typeSystem: TypeSystem): ScUndefinedSubstitutor = {
    if (typeSystem eq ScalaTypeSystem) emptyScala
    else if (typeSystem eq DottyTypeSystem) emptyDotty
    else new ScUndefinedSubstitutorImpl()
  }

  def multi(subs: Seq[ScUndefinedSubstitutor]) = new ScMultiUndefinedSubstitutor(subs)

  private val emptyScala = new ScUndefinedSubstitutorImpl()(ScalaTypeSystem)
  private val emptyDotty = new ScUndefinedSubstitutorImpl()(DottyTypeSystem)
}

private class ScUndefinedSubstitutorImpl(val upperMap: Map[(String, Long), Set[ScType]] = Map.empty,
                                         val lowerMap: Map[(String, Long), Set[ScType]] = Map.empty,
                                         val upperAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty,
                                         val lowerAdditionalMap: Map[(String, Long), Set[ScType]] = Map.empty)
                                        (implicit typeSystem: TypeSystem)
  extends ScUndefinedSubstitutor {

  def copy(upperMap: Map[(String, Long), Set[ScType]] = upperMap,
           lowerMap: Map[(String, Long), Set[ScType]] = lowerMap,
           upperAdditionalMap: Map[(String, Long), Set[ScType]] = upperAdditionalMap,
           lowerAdditionalMap: Map[(String, Long), Set[ScType]] = lowerAdditionalMap): ScUndefinedSubstitutor = {
    ScUndefinedSubstitutor(upperMap, lowerMap, upperAdditionalMap, lowerAdditionalMap)
  }

  def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty && upperAdditionalMap.isEmpty && lowerAdditionalMap.isEmpty

  //todo: this is can be rewritten in more fast way
  def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
    added match {
      case subst: ScUndefinedSubstitutorImpl =>
        var res: ScUndefinedSubstitutor = this
        for ((name, seq) <- subst.upperMap) {
          for (upper <- seq) {
            res = res.addUpper(name, upper, variance = 0)
          }
        }
        for ((name, seq) <- subst.lowerMap) {
          for (lower <- seq) {
            res = res.addLower(name, lower, variance = 0)
          }
        }

        for ((name, seq) <- subst.upperAdditionalMap) {
          for (upper <- seq) {
            res = res.addUpper(name, upper, additional = true, variance = 0)
          }
        }
        for ((name, seq) <- subst.lowerAdditionalMap) {
          for (lower <- seq) {
            res = res.addLower(name, lower, additional = true, variance = 0)
          }
        }

        res
      case subst: ScMultiUndefinedSubstitutor =>
        subst.addSubst(this)
    }
  }

  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Int = -1): ScUndefinedSubstitutor = {
    var index = 0
    val lower = (_lower match {
      case ScAbstractType(_, absLower, _) =>
        if (absLower.equiv(Nothing)) return this
        absLower //upper will be added separately
      case _ =>
        _lower.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
          case (ScAbstractType(_, absLower, upper), i, data) =>
            i match {
              case -1 => (true, absLower, data)
              case 1 => (true, upper, data)
              case 0 => (true, absLower/*ScExistentialArgument(s"_$$${index += 1; index}", Nil, absLower, upper)*/, data) //todo: why this is right?
            }
          case (ScExistentialArgument(nm, _, skoLower, upper), i, data) if !data.contains(nm) =>
            i match {
              case -1 => (true, skoLower, data)
              case 1 => (true, upper, data)
              case 0 => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, skoLower, upper), data)
            }
          case (ex: ScExistentialType, _, data) => (false, ex, data ++ ex.boundNames)
          case (tp, _, data) => (false, tp, data)
        }, variance)
    }).unpackedType
    val lMap = if (additional) lowerAdditionalMap else lowerMap
    lMap.get(name) match {
      case Some(set: Set[ScType]) =>
        if (additional) copy(lowerAdditionalMap = lMap.updated(name, set + lower))
        else copy(lowerMap = lMap.updated(name, set + lower))
      case None =>
        if (additional) copy(lowerAdditionalMap = lMap + ((name, Set(lower))))
        else copy(lowerMap = lMap + ((name, Set(lower))))
    }
  }

  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Int = 1): ScUndefinedSubstitutor = {
    var index = 0
    val upper =
      (_upper match {
        case ScAbstractType(_, _, absUpper) if variance == 0 =>
          if (absUpper.equiv(Any)) return this
          absUpper // lower will be added separately
        case ScAbstractType(_, _, absUpper) if variance == 1 && absUpper.equiv(Any) => return this
        case _ =>
          _upper.recursiveVarianceUpdateModifiable[Set[String]](Set.empty, {
              case (ScAbstractType(_, lower, absUpper), i, data) =>
                i match {
                  case -1 => (true, lower, data)
                  case 1 => (true, absUpper, data)
                  case 0 => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, absUpper), data) //todo: why this is right?
                }
              case (ScExistentialArgument(nm, _, lower, skoUpper), i, data) if !data.contains(nm) =>
                i match {
                  case -1 => (true, lower, data)
                  case 1 => (true, skoUpper, data)
                  case 0 => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, skoUpper), data)
                }
              case (ex: ScExistentialType, _, data) => (false, ex, data ++ ex.boundNames)
              case (tp, _, data) => (false, tp, data)
            }, variance)
      }).unpackedType
    val uMap = if (additional) upperAdditionalMap else upperMap
    uMap.get(name) match {
      case Some(set: Set[ScType]) =>
        if (additional) copy(upperAdditionalMap = uMap.updated(name, set + upper))
        else copy(upperMap = uMap.updated(name, set + upper))
      case None =>
        if (additional) copy(upperAdditionalMap = uMap + ((name, Set(upper))))
        else copy(upperMap = uMap + ((name, Set(upper))))
    }
  }

  lazy val additionalNames: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    lowerAdditionalMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ upperAdditionalMap.keySet
  }

  lazy val names: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    upperMap.keySet ++ lowerMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ additionalNames
  }

  def getSubstitutorWithBounds(notNonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])] = {
    var tvMap = Map.empty[Name, ScType]
    var lMap = Map.empty[Name, ScType]
    var uMap = Map.empty[Name, ScType]

    def solve(name: Name, visited: Set[Name]): Option[ScType] = {
      if (visited.contains(name)) {
        tvMap += ((name, Nothing))
        return None
      }
      tvMap.get(name) match {
        case Some(tp) => Some(tp)
        case _ =>
          (lowerMap.get(name).map(set => lowerAdditionalMap.get(name) match {
            case Some(set1) => set ++ set1
            case _ => set
          }) match {
            case Some(set) => Some(set)
            case _ => lowerAdditionalMap.get(name)
          }) match {
            case Some(set) =>
              var res = false
              def checkRecursive(tp: ScType): Boolean = {
                tp.recursiveUpdate {
                  case tpt: TypeParameterType =>
                    val otherName = tpt.nameAndId
                    if (additionalNames.contains(otherName)) {
                        res = true
                        solve(otherName, visited + name) match {
                          case None if !notNonable => return false
                          case _ =>
                        }
                    }
                    (false, tpt)
                  case UndefinedType(tpt, _) =>
                    val otherName = tpt.nameAndId
                    if (names.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case tp: ScType => (false, tp)
                }
                true
              }
              val seqIterator = set.iterator
              while (seqIterator.hasNext) {
                val p = seqIterator.next()
                if (!checkRecursive(p)) {
                  tvMap += ((name, Nothing))
                  return None
                }
              }
              if (set.nonEmpty) {
                val subst = if (res) ScSubstitutor(tvMap) else ScSubstitutor.empty
                var lower: ScType = Nothing
                val setIterator = set.iterator
                while (setIterator.hasNext) {
                  lower = lower.lub(subst.subst(setIterator.next()), checkWeak = true)
                }
                lMap += ((name, lower))
                tvMap += ((name, lower))
              }
            case None =>
          }
          (upperMap.get(name).map(set => upperAdditionalMap.get(name) match {
            case Some(set1) => set ++ set1
            case _ => set
          }) match {
            case Some(set) => Some(set)
            case _ => upperAdditionalMap.get(name)
          }) match {
            case Some(set) =>
              var res = false
              def checkRecursive(tp: ScType): Boolean = {
                tp.recursiveUpdate {
                  case tpt: TypeParameterType =>
                    val otherName = tpt.nameAndId
                    if (additionalNames.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case UndefinedType(tpt, _) =>
                    val otherName = tpt.nameAndId
                    if (names.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case tp: ScType => (false, tp)
                }
                true
              }
              val seqIterator = set.iterator
              while (seqIterator.hasNext) {
                val p = seqIterator.next()
                if (!checkRecursive(p)) {
                  tvMap += ((name, Nothing))
                  return None
                }
              }
              if (set.nonEmpty) {
                var uType: ScType = Nothing
                val subst = if (res) ScSubstitutor(tvMap) else ScSubstitutor.empty
                val size: Int = set.size
                if (size == 1) {
                  uType = subst.subst(set.iterator.next())
                  uMap += ((name, uType))
                } else if (size > 1) {
                  var upper: ScType = Any
                  val setIterator = set.iterator
                  while (setIterator.hasNext) {
                    upper = upper.glb(subst.subst(setIterator.next()), checkWeak = false)
                  }
                  uType = upper
                  uMap += ((name, uType))
                }
                tvMap.get(name) match {
                  case Some(lower) =>
                    if (!notNonable) {
                      val seqIterator = set.iterator
                      while (seqIterator.hasNext) {
                        val upper = seqIterator.next()
                        if (!lower.conforms(subst.subst(upper))) {
                          return None
                        }
                      }
                    }
                  case None => tvMap += ((name, uType))
                }
              }
            case None =>
          }

          if (tvMap.get(name).isEmpty) {
            tvMap += ((name, Nothing))
          }
          tvMap.get(name)
      }
    }
    val namesIterator = names.iterator
    while (namesIterator.hasNext) {
      val name = namesIterator.next()
      solve(name, Set.empty) match {
        case Some(_) => // do nothing
        case None if !notNonable => return None
        case _ =>
      }
    }
    val subst = ScSubstitutor(tvMap)
    Some((subst, lMap, uMap))
  }

  def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor = {
    ScUndefinedSubstitutor(
      upperMap.filter(fun),
      lowerMap.filter(fun),
      upperAdditionalMap.filter(fun),
      lowerAdditionalMap.filter(fun))
  }
}

class ScMultiUndefinedSubstitutor(val subs: Seq[ScUndefinedSubstitutor]) extends ScUndefinedSubstitutor {
  def copy(subs: Seq[ScUndefinedSubstitutor]) = new ScMultiUndefinedSubstitutor(subs)

  override def addLower(name: (String, Long), _lower: ScType, additional: Boolean, variance: Int): ScUndefinedSubstitutor =
    copy(subs.map(_.addLower(name, _lower, additional, variance)))

  override def addUpper(name: (String, Long), _upper: ScType, additional: Boolean, variance: Int): ScUndefinedSubstitutor =
    copy(subs.map(_.addUpper(name, _upper, additional, variance)))

  override def getSubstitutorWithBounds(notNonable: Boolean): Option[(ScSubstitutor, Map[Name, ScType], Map[Name, ScType])] =
    subs.map(_.getSubstitutorWithBounds(notNonable)).find(_.isDefined).getOrElse(None)

  override def filter(fun: (((String, Long), Set[ScType])) => Boolean): ScUndefinedSubstitutor =
    copy(subs.map(_.filter(fun)))

  override def addSubst(added: ScUndefinedSubstitutor): ScUndefinedSubstitutor = copy(subs.map(_.addSubst(added)))

  override def isEmpty: Boolean = subs.forall(_.isEmpty)

  override def names: Set[(String, Long)] = if (subs.isEmpty) Set.empty else subs.tail.map(_.names).
    foldLeft(subs.head.names){case (a, b) => a.intersect(b)}
}