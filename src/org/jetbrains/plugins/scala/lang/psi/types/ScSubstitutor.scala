package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.collection.immutable.{HashMap, HashSet, Map}

/**
* @author ven
*/
object ScSubstitutor {
  val empty: ScSubstitutor = new ScSubstitutor() {
    override def toString: String = "Empty substitutor"
  }

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")

  private val followLimit = 800
}

class ScSubstitutor(val tvMap: Map[(String, PsiElement), ScType],
                    val aliasesMap: Map[String, Suspension[ScType]],
                    val updateThisType: Option[ScType]) {
  //use ScSubstitutor.empty instead
  private[ScSubstitutor] def this() = this(Map.empty, Map.empty, None)

  def this(updateThisType: ScType) {
    this(Map.empty, Map.empty, Some(updateThisType))
  }

  def this(tvMap: Map[(String, PsiElement), ScType],
                    aliasesMap: Map[String, Suspension[ScType]],
                    updateThisType: Option[ScType],
                    follower: ScSubstitutor) = {
    this(tvMap, aliasesMap, updateThisType)
    this.follower = follower
  }

  //todo: this is excluded from constructor, can cause lots of bugs, probably it should be rewritten in more appropriate way
  private var myDependentMethodTypesFun: () => Map[Parameter, ScType] = () => Map.empty
  private var myDependentMethodTypesFunDefined: Boolean = false
  private var myDependentMethodTypes: Map[Parameter, ScType] = null
  private def getDependentMethodTypes: Map[Parameter, ScType] = {
    if (myDependentMethodTypes == null) {
      myDependentMethodTypes = myDependentMethodTypesFun()
    }
    myDependentMethodTypes
  }

  def this(dependentMethodTypes: () => Map[Parameter, ScType]) {
    this()
    myDependentMethodTypesFun = dependentMethodTypes
    myDependentMethodTypesFunDefined = true
  }

  private var follower: ScSubstitutor = null

  def getFollower: ScSubstitutor = follower

  override def toString: String =
    s"ScSubstitutor($tvMap, $aliasesMap, $updateThisType)${ if (follower != null) " >> " + follower.toString else "" }"

  def bindT(name : (String, PsiElement), t: ScType) = {
    val res = new ScSubstitutor(tvMap + ((name, t)), aliasesMap, updateThisType, follower)
    res.myDependentMethodTypesFun = myDependentMethodTypesFun
    res.myDependentMethodTypesFunDefined = myDependentMethodTypesFunDefined
    res.myDependentMethodTypes = myDependentMethodTypes
    res
  }

  def bindA(name: String, f: () => ScType) = {
    val res = new ScSubstitutor(tvMap, aliasesMap + ((name, new Suspension[ScType](f))), updateThisType, follower)
    res.myDependentMethodTypesFun = myDependentMethodTypesFun
    res.myDependentMethodTypesFunDefined = myDependentMethodTypesFunDefined
    res.myDependentMethodTypes = myDependentMethodTypes
    res
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
        var zSubst = new ScSubstitutor(Map.empty, Map.empty, Some(ScThisType(template)))
        var placer = template.getContext
        while (placer != null) {
          placer match {
            case t: ScTemplateDefinition => zSubst = zSubst.followed(
              new ScSubstitutor(Map.empty, Map.empty, Some(ScThisType(t)))
            )
            case _ =>
          }
          placer = placer.getContext
        }
        zSubst.followed(this)
      case _ => new ScSubstitutor(Map.empty, Map.empty, Some(tp)).followed(this)
    }
  }
  def followed(s: ScSubstitutor): ScSubstitutor = followed(s, 0)

  def isUpdateThisSubst: Option[ScType] = {
    if (tvMap.size + aliasesMap.size == 0 && !myDependentMethodTypesFunDefined) updateThisType
    else None
  }

  private def followed(s: ScSubstitutor, level: Int): ScSubstitutor = {
    if (level > ScSubstitutor.followLimit)
      throw new RuntimeException("Too much followers for substitutor: " + this.toString)
    if (follower == null && tvMap.size + aliasesMap.size  == 0 && updateThisType.isEmpty && !myDependentMethodTypesFunDefined) s
    else if (s.getFollower == null && s.tvMap.size + s.aliasesMap.size == 0 && s.updateThisType.isEmpty && !s.myDependentMethodTypesFunDefined) this
    else {
      val res = new ScSubstitutor(tvMap, aliasesMap, updateThisType,
        if (follower != null) follower followed (s, level + 1) else s)
      res.myDependentMethodTypesFun = myDependentMethodTypesFun
      res.myDependentMethodTypesFunDefined = myDependentMethodTypesFunDefined
      res.myDependentMethodTypes = myDependentMethodTypes
      res
    }
  }

  def subst(t: ScType): ScType = try {
    if (follower != null) follower.subst(substInternal(t)) else substInternal(t)
  } catch {
    case s: StackOverflowError =>
      throw new RuntimeException("StackOverFlow during ScSubstitutor.subst(" + t + ") this = " + this, s)
  }

  private def extractTpt(tpt: ScTypeParameterType, t: ScType): ScType = {
    if (tpt.args.isEmpty) t
    else t match {
      case ScParameterizedType(designator, _) => designator
      case _ => t
    }
  }

  protected def substInternal(t: ScType) : ScType = {
    var result: ScType = t
    val visitor = new ScalaTypeVisitor {
      override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = {
        val ScTypePolymorphicType(internalType, typeParameters) = t
        result = ScTypePolymorphicType(substInternal(internalType), typeParameters.map(tp => {
          TypeParameter(tp.name, tp.typeParams /* todo: is it important here to update? */,
            () => substInternal(tp.lowerType()), () => substInternal(tp.upperType()), tp.ptp)
        }))
      }

      override def visitAbstractType(a: ScAbstractType): Unit = {
        result = tvMap.get((a.tpt.name, a.tpt.getId)) match {
          case None => a
          case Some(v) => v match {
            case tpt: ScTypeParameterType if tpt.param == a.tpt.param => a
            case _ => extractTpt(a.tpt, v)
          }
        }
      }

      override def visitMethodType(m: ScMethodType): Unit = {
        val ScMethodType(retType, params, isImplicit) = m
        result = new ScMethodType(substInternal(retType),
          params.map(p => p.copy(paramType = substInternal(p.paramType),
            expectedType = substInternal(p.expectedType), defaultType = p.defaultType.map(substInternal))), isImplicit)(m.project, m.scope)
      }

      override def visitUndefinedType(u: ScUndefinedType): Unit = {
        result = tvMap.get((u.tpt.name, u.tpt.getId)) match {
          case None => u
          case Some(v) => v match {
            case tpt: ScTypeParameterType if tpt.param == u.tpt.param => u
            case _ => extractTpt(u.tpt, v)
          }
        }
      }

      override def visitTypeVariable(tv: ScTypeVariable): Unit = {
        result = tvMap.get((tv.name, null)) match {
          case None => tv
          case Some(v) => v
        }
      }

      override def visitTypeParameterType(tpt: ScTypeParameterType): Unit = {
        result = tvMap.get((tpt.name, tpt.getId)) match {
          case None => tpt
          case Some(v) => extractTpt(tpt, v)
        }
      }

      override def visitDesignatorType(d: ScDesignatorType): Unit = {
        if (getDependentMethodTypes.nonEmpty) {
          result = getDependentMethodTypes.find {
            case (parameter: Parameter, tp: ScType) =>
              parameter.paramInCode match {
                case Some(p) if p == d.element => true
                case _ => false
              }
          } match {
            case Some((_, res)) => res
            case _ => t
          }
        }
      }

      override def visitThisType(th: ScThisType): Unit = {
        val clazz = th.clazz
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
              ScType.extractDesignated(typez, withoutAliases = true) match {
                case Some((t: ScTypeDefinition, subst)) =>
                  if (t == clazz) tp
                  else if (ScalaPsiUtil.cachedDeepIsInheritor(t, clazz)) tp
                  else {
                    t.selfType match {
                      case Some(selfType) =>
                        ScType.extractDesignated(selfType, withoutAliases = true) match {
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
                                  ScType.extractClass(tps) match {
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
                case Some((cl: PsiClass, subst)) =>
                  typez match {
                    case t: ScTypeParameterType => return update(t.upper.v)
                    case p@ScParameterizedType(des, typeArgs) =>
                      p.designator match {
                        case ScTypeParameterType(_, _, _, upper, _) => return update(p.substitutor.subst(upper.v))
                        case _ =>
                      }
                    case _ =>
                  }
                  if (cl == clazz) tp
                  else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) tp
                  else null
                case Some((named: ScTypedDefinition, subst)) =>
                  update(named.getType(TypingContext.empty).getOrAny)
                case _ =>
                  typez match {
                    case ScCompoundType(types, _, _) =>
                      val iter = types.iterator
                      while (iter.hasNext) {
                        val tps = iter.next()
                        ScType.extractClass(tps) match {
                          case Some(cl) =>
                            if (cl == clazz) return tp
                            else if (ScalaPsiUtil.cachedDeepIsInheritor(cl, clazz)) return tp
                          case _ =>
                        }
                      }
                    case t: ScTypeParameterType => return update(t.upper.v)
                    case p@ScParameterizedType(des, typeArgs) =>
                      p.designator match {
                        case ScTypeParameterType(_, _, _, upper, _) => return update(p.substitutor.subst(upper.v))
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
                case ScParameterizedType(ScProjectionType(newType, _, _), _) => tp = newType
                case _ => tp = null
              }
            }
            t
          case _ => t
        }
      }

      override def visitExistentialType(ex: ScExistentialType): Unit = {
        val ScExistentialType(q, wildcards) = ex
        //remove bound names
        val trunc = aliasesMap -- ex.boundNames
        val substCopy = new ScSubstitutor(tvMap, trunc, updateThisType, follower)
        substCopy.myDependentMethodTypesFun = myDependentMethodTypesFun
        substCopy.myDependentMethodTypesFunDefined = myDependentMethodTypesFunDefined
        substCopy.myDependentMethodTypes = myDependentMethodTypes
        result = new ScExistentialType(substCopy.substInternal(q),
          wildcards.map(_.subst(ScSubstitutor.this)))
      }

      override def visitParameterizedType(pt: ScParameterizedType): Unit = {
        val typeArgs = pt.typeArgs
        result = pt.designator match {
          case tpt: ScTypeParameterType =>
            tvMap.get((tpt.name, tpt.getId)) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (tpt.args.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs.map(substInternal))
                }
              case _ =>
                substInternal(tpt) match {
                  case ScParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case u: ScUndefinedType =>
            tvMap.get((u.tpt.name, u.tpt.getId)) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (u.tpt.args.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs map substInternal)
                }
              case _ =>
                substInternal(u) match {
                  case ScParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case u: ScAbstractType =>
            tvMap.get((u.tpt.name, u.tpt.getId)) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (u.tpt.args.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs map substInternal)
                }
              case _ =>
                substInternal(u) match {
                  case ScParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case designator =>
            substInternal(designator) match {
              case ScParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
              case des => ScParameterizedType(des, typeArgs map substInternal)
            }
        }
      }

      override def visitJavaArrayType(j: JavaArrayType): Unit = {
        result = JavaArrayType(substInternal(j.arg))
      }

      override def visitProjectionType(p: ScProjectionType): Unit = {
        val ScProjectionType(proj, element, s) = p
        val res = ScProjectionType(substInternal(proj), element, s)
        result = res match {
          case res: ScProjectionType if !s =>
            val actualElement = p.actualElement
            if (actualElement.isInstanceOf[ScTypeDefinition] &&
              actualElement != res.actualElement) res.copy(superReference = true)
            else res
          case _ => res
        }
      }

      override def visitCompoundType(comp: ScCompoundType): Unit = {
        val ScCompoundType(comps, signatureMap, typeMap) = comp
        val substCopy = new ScSubstitutor(tvMap, aliasesMap, updateThisType)
        substCopy.myDependentMethodTypesFun = myDependentMethodTypesFun
        substCopy.myDependentMethodTypesFunDefined = myDependentMethodTypesFunDefined
        substCopy.myDependentMethodTypes = myDependentMethodTypes
        def substTypeParam(tp: TypeParameter): TypeParameter = {
          new TypeParameter(tp.name, tp.typeParams.map(substTypeParam), () => substInternal(tp.lowerType()),
            () => substInternal(tp.upperType()), tp.ptp)
        }
        val middleRes = ScCompoundType(comps.map(substInternal), signatureMap.map {
          case (s: Signature, tp: ScType) =>
            val pTypes: List[Seq[() => ScType]] = s.substitutedTypes.map(_.map(f => () => substInternal(f())))
            val tParams: Array[TypeParameter] = if (s.typeParams.length == 0) TypeParameter.EMPTY_ARRAY else s.typeParams.map(substTypeParam)
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

class ScUndefinedSubstitutor(val upperMap: Map[(String, PsiElement), HashSet[ScType]] = HashMap.empty,
                             val lowerMap: Map[(String, PsiElement), HashSet[ScType]] = HashMap.empty,
                             val upperAdditionalMap: Map[(String, PsiElement), HashSet[ScType]] = HashMap.empty,
                             val lowerAdditionalMap: Map[(String, PsiElement), HashSet[ScType]] = HashMap.empty)
                            (implicit val typeSystem: TypeSystem) {

  def copy(upperMap: Map[(String, PsiElement), HashSet[ScType]] = upperMap,
           lowerMap: Map[(String, PsiElement), HashSet[ScType]] = lowerMap,
           upperAdditionalMap: Map[(String, PsiElement), HashSet[ScType]] = upperAdditionalMap,
           lowerAdditionalMap: Map[(String, PsiElement), HashSet[ScType]] = lowerAdditionalMap): ScUndefinedSubstitutor = {
    new ScUndefinedSubstitutor(upperMap, lowerMap, upperAdditionalMap, lowerAdditionalMap)
  }

  type Name = (String, PsiElement)

  def isEmpty: Boolean = upperMap.isEmpty && lowerMap.isEmpty && upperAdditionalMap.isEmpty && lowerAdditionalMap.isEmpty

  //todo: this is can be rewritten in more fast way
  def addSubst(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = {
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
  }

  def +(subst: ScUndefinedSubstitutor): ScUndefinedSubstitutor = addSubst(subst)

  def addLower(name: Name, _lower: ScType, additional: Boolean = false, variance: Int = -1): ScUndefinedSubstitutor = {
    var index = 0
    val lower = (_lower match {
      case ScAbstractType(_, absLower, upper) =>
        if (absLower.equiv(Nothing)) return this
        absLower //upper will be added separately
      case _ =>
        _lower.recursiveVarianceUpdate((tp: ScType, i: Int) => {
          tp match {
            case ScAbstractType(_, absLower, upper) =>
              i match {
                case -1 => (true, absLower)
                case 1 => (true, upper)
                case 0 => (true, absLower/*ScSkolemizedType(s"_$$${index += 1; index}", Nil, absLower, upper)*/) //todo: why this is right?
              }
            case ScSkolemizedType(_, _, skoLower, upper) =>
              i match {
                case -1 => (true, skoLower)
                case 1 => (true, upper)
                case 0 => (true, ScSkolemizedType(s"_$$${index += 1; index}", Nil, skoLower, upper))
              }
            case _ => (false, tp)
          }
        }, variance)
    }).unpackedType
    val lMap = if (additional) lowerAdditionalMap else lowerMap
    lMap.get(name) match {
      case Some(set: HashSet[ScType]) =>
        if (additional) copy(lowerAdditionalMap = lMap.updated(name, set + lower))
        else copy(lowerMap = lMap.updated(name, set + lower))
      case None =>
        if (additional) copy(lowerAdditionalMap = lMap + ((name, HashSet(lower))))
        else copy(lowerMap = lMap + ((name, HashSet(lower))))
    }
  }

  def addUpper(name: Name, _upper: ScType, additional: Boolean = false, variance: Int = 1): ScUndefinedSubstitutor = {
    var index = 0
    val upper =
      (_upper match {
        case ScAbstractType(_, lower, absUpper) if variance == 0 =>
          if (absUpper.equiv(Any)) return this
          absUpper // lower will be added separately
        case ScAbstractType(_, lower, absUpper) if variance == 1 && absUpper.equiv(Any) => return this
        case _ =>
          _upper.recursiveVarianceUpdate((tp: ScType, i: Int) => {
            tp match {
              case ScAbstractType(_, lower, absUpper) =>
                i match {
                  case -1 => (true, lower)
                  case 1 => (true, absUpper)
                  case 0 => (true, ScSkolemizedType(s"_$$${index += 1; index}", Nil, lower, absUpper)) //todo: why this is right?
                }
              case ScSkolemizedType(_, _, lower, skoUpper) =>
                i match {
                  case -1 => (true, lower)
                  case 1 => (true, skoUpper)
                  case 0 => (true, ScSkolemizedType(s"_$$${index += 1; index}", Nil, lower, skoUpper))
                }
              case _ => (false, tp)
            }
          }, variance)
      }).unpackedType
    val uMap = if (additional) upperAdditionalMap else upperMap
    uMap.get(name) match {
      case Some(set: HashSet[ScType]) =>
        if (additional) copy(upperAdditionalMap = uMap.updated(name, set + upper))
        else copy(upperMap = uMap.updated(name, set + upper))
      case None =>
        if (additional) copy(upperAdditionalMap = uMap + ((name, HashSet(upper))))
        else copy(upperMap = uMap + ((name, HashSet(upper))))
    }
  }

  def getSubstitutor: Option[ScSubstitutor] = getSubstitutor(notNonable = false)

  val additionalNames: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    lowerAdditionalMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ upperAdditionalMap.keySet
  }

  val names: Set[Name] = {
    //We need to exclude Nothing names from this set, see SCL-5736
    upperMap.keySet ++ lowerMap.filter(_._2.exists(!_.equiv(Nothing))).keySet ++ additionalNames
  }

  import scala.collection.immutable.{HashMap => IHashMap}
  import scala.collection.mutable.{HashMap => MHashMap}
  val lMap = new MHashMap[Name, ScType]
  val rMap = new MHashMap[Name, ScType]

  def getSubstitutor(notNonable: Boolean): Option[ScSubstitutor] = {
    import scala.collection.immutable.HashSet
    val tvMap = new MHashMap[Name, ScType]

    def solve(name: Name, visited: HashSet[Name]): Option[ScType] = {
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
                  case tpt: ScTypeParameterType =>
                    val otherName = (tpt.name, tpt.getId)
                    if (additionalNames.contains(otherName)) {
                        res = true
                        solve(otherName, visited + name) match {
                          case None if !notNonable => return false
                          case _ =>
                        }
                    }
                    (false, tpt)
                  case ScUndefinedType(tpt) =>
                    val otherName = (tpt.name, tpt.getId)
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
                val subst = if (res) new ScSubstitutor(IHashMap.empty ++ tvMap, Map.empty, None) else ScSubstitutor.empty
                var lower: ScType = Nothing
                val setIterator = set.iterator
                while (setIterator.hasNext) {
                  lower = lower.lub(subst.subst(setIterator.next()))
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
                  case tpt: ScTypeParameterType =>
                    val otherName = (tpt.name, tpt.getId)
                    if (additionalNames.contains(otherName)) {
                      res = true
                      solve(otherName, visited + name) match {
                        case None if !notNonable => return false
                        case _ =>
                      }
                    }
                    (false, tpt)
                  case ScUndefinedType(tpt) =>
                    val otherName = (tpt.name, tpt.getId)
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
                var rType: ScType = Nothing
                val subst = if (res) new ScSubstitutor(IHashMap.empty ++ tvMap, Map.empty, None) else ScSubstitutor.empty
                val size: Int = set.size
                if (size == 1) {
                  rType = subst.subst(set.iterator.next())
                  rMap += ((name, rType))
                } else if (size > 1) {
                  var upper: ScType = Any
                  val setIterator = set.iterator
                  while (setIterator.hasNext) {
                    upper = upper.glb(subst.subst(setIterator.next()), checkWeak = false)
                  }
                  rType = upper
                  rMap += ((name, rType))
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
                  case None => tvMap += ((name, rType))
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
      solve(name, HashSet.empty) match {
        case Some(tp) => // do nothing
        case None if !notNonable => return None
        case _ =>
      }
    }
    val subst = new ScSubstitutor(IHashMap.empty ++ tvMap, Map.empty, None)
    Some(subst)
  }
}
