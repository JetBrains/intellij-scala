package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{NonValueType, ScMethodType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.annotation.tailrec
import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/*
Current types for pattern matching, this approach is bad for many reasons (one of them is bad performance).
Better to use OOP approach instead.
match {
 case Any =>
 case Null =>
 case AnyRef =>
 case Nothing =>
 case Singleton =>
 case AnyVal =>
 case Unit =>
 case Boolean =>
 case Char =>
 case Int =>
 case Long =>
 case Float =>
 case Double =>
 case Byte =>
 case Short =>
 case ScFunctionType(returnType, params) =>
 case ScTupleType(components) =>
 case ScCompoundType(components, decls, typeDecls) =>
 case ScProjectionType(projected, element, subst) =>
 case JavaArrayType(arg) =>
 case ScParameterizedType(designator, typeArgs) =>
 case ScExistentialType(quantified, wildcards) =>
 case ScThisType(clazz) =>
 case ScDesignatorType(element) =>
 case ScTypeParameterType(name, args, lower, upper, param) =>
 case ScExistentialArgument(name, args, lowerBound, upperBound) =>
 case ScSkolemizedType(name, args, lower, upper) =>
 case ScTypeVariable(name) =>
 case ScUndefinedType(tpt) =>
 case ScMethodType(returnType, params, isImplicit) =>
 case ScAbstractType(tpt, lower, upper) =>
 case ScTypePolymorphicType(internalType, typeParameters) =>
}
*/
trait ScType {
  private var aliasType: Option[AliasType] = null

  final def isAliasType: Option[AliasType] = {
    if (aliasType == null) {
      aliasType = isAliasTypeInner
    }
    aliasType
  }

  protected def isAliasTypeInner: Option[AliasType] = None

  final def equiv(t: ScType): Boolean = Equivalence.equiv(this, t)

  /**
   * Checks, whether the following assignment is correct:
   * val x: t = (y: this)
   */
  final def conforms(t: ScType, checkWeak: Boolean = false): Boolean = Conformance.conforms(t, this, checkWeak)

  final def weakConforms(t: ScType): Boolean = Conformance.conforms(t, this, checkWeak = true)

  final def presentableText = ScType.presentableText(this)

  final def canonicalText = ScType.canonicalText(this)

  override def toString = presentableText

  def isValue: Boolean

  final def isStable: Boolean = ScType.isStable(this)

  def isFinalType: Boolean = false

  def inferValueType: ValueType

  def unpackedType: ScType = {
    val wildcards = new ArrayBuffer[ScExistentialArgument]
    val quantified = recursiveUpdate({
      case s: ScSkolemizedType =>
        wildcards += ScExistentialArgument(s.name, s.args, s.lower, s.upper)
        (true, ScTypeVariable(s.name))
      case t => (false, t)
    })
    if (wildcards.length > 0) {
      ScExistentialType(quantified, wildcards.toList).simplify()
    } else quantified
  }

  /**
   * This method is important for parameters expected type.
   * There shouldn't be any abstract type in this expected type.
   * todo rewrite with recursiveUpdate method
   */
  def removeAbstracts: ScType = this

  def removeVarianceAbstracts(variance: Int): ScType = {
    var index = 0
    recursiveVarianceUpdate((tp: ScType, i: Int) => {
      tp match {
        case ScAbstractType(_, lower, upper) =>
          i match {
            case -1 => (true, lower)
            case 1 => (true, upper)
            case 0 => (true, ScSkolemizedType(s"_$$${index += 1; index}", Nil, lower, upper))
          }
        case _ => (false, tp)
      }
    }, variance).unpackedType
  }

  def removeUndefines(): ScType = {
    recursiveUpdate {
      case u: ScUndefinedType => (true, Any)
      case tp: ScType => (false, tp)
    }
  }

  def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (false, uSubst)
  }

  class RecursiveUpdateException extends Exception {
    override def getMessage: String = "Type mismatch after update method"
  }

  import scala.collection.immutable.{HashSet => IHashSet}

  /**
   * use 'update' to replace appropriate type part with another type
   * 'update' should return true if type changed, false otherwise.
   * To just collect info about types (see collectAbstracts) always return false
   *
   * default implementation for types, which don't contain other types.
   */
  def recursiveUpdate(update: ScType => (Boolean, ScType), visited: IHashSet[ScType] = IHashSet.empty): ScType = {
    val res = update(this)
    if (res._1) res._2
    else this
  }

  def recursiveVarianceUpdate(update: (ScType, Int) => (Boolean, ScType), variance: Int = 1): ScType = {
    recursiveVarianceUpdateModifiable[Unit]((), (tp, v, T) => {
      val (newTp, newV) = update(tp, v)
      (newTp, newV, ())
    }, variance)
  }

  def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    val res = update(this, variance, data)
    if (res._1) res._2
    else this
  }

  def collectAbstracts: Seq[ScAbstractType] = {
    val set: mutable.HashSet[ScAbstractType] = new mutable.HashSet[ScAbstractType]

    recursiveUpdate(tp => {
      tp match {
        case a: ScAbstractType => set += a
        case _ =>
      }
      (false, tp)
    })

    set.toSeq
  }

  def getValType: Option[StdType] = None

  def visitType(visitor: ScalaTypeVisitor)
}

object ScType extends ScTypePresentation with ScTypePsiTypeBridge {
  val baseTypesQualMap: Map[String, StdType] = HashMap(
    "scala.Unit" -> Unit,
    "scala.Boolean" -> Boolean,
    "scala.Char" -> Char,
    "scala.Int" -> Int,
    "scala.Long" -> Long,
    "scala.Float" -> Float,
    "scala.Double" -> Double,
    "scala.Byte" -> Byte,
    "scala.AnyVal" -> AnyVal
  )

  def extractClass(t: ScType, project: Option[Project] = None): Option[PsiClass] = {
    t match {
      case p@ScParameterizedType(t1, _) => extractClass(t1, project) //performance improvement
      case _ => extractClassType(t, project).map(_._1)
    }
  }

  def extractClassType(t: ScType, project: Option[Project] = None): Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case n: NonValueType => extractClassType(n.inferValueType)
    case ScThisType(clazz) => Some(clazz, new ScSubstitutor(t))
    case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
    case ScDesignatorType(ta: ScTypeAliasDefinition) =>
      val result = ta.aliasedType(TypingContext.empty)
      if (result.isEmpty) return None
      extractClassType(result.get)
    case proj@ScProjectionType(p, elem, _) => proj.actualElement match {
      case c: PsiClass => Some((c, proj.actualSubst))
      case t: ScTypeAliasDefinition =>
        val result = t.aliasedType(TypingContext.empty)
        if (result.isEmpty) return None
        extractClassType(proj.actualSubst.subst(result.get))
      case _ => None
    }
    case p@ScParameterizedType(t1, _) =>
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    case std@StdType(_, _) =>
      val asClass = std.asClass(project.getOrElse(DecompilerUtil.obtainProject))
      if (asClass.isEmpty) return None
      Some((asClass.get, ScSubstitutor.empty))
    case _ => None
  }

  /**
   * Returns named element associated with type `t`.
   * If withoutAliases is `true` expands alias definitions first
   * @param t type
   * @param withoutAliases need to expand alias or not
   * @return element and substitutor
   */
  def extractDesignated(t: ScType, withoutAliases: Boolean): Option[Pair[PsiNamedElement, ScSubstitutor]] = t match {
    case n: NonValueType => extractDesignated(n.inferValueType, withoutAliases)
    case ScDesignatorType(ta: ScTypeAliasDefinition) if withoutAliases =>
      val result = ta.aliasedType(TypingContext.empty)
      if (result.isEmpty) return None
      extractDesignated(result.get, withoutAliases)
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case ScThisType(c) => Some(c, ScSubstitutor.empty)
    case proj@ScProjectionType(p, e, _) => proj.actualElement match {
      case t: ScTypeAliasDefinition if withoutAliases =>
        val result = t.aliasedType(TypingContext.empty)
        if (result.isEmpty) return None
        extractDesignated(proj.actualSubst.subst(result.get), withoutAliases)
      case _ => Some((proj.actualElement, proj.actualSubst))
    }
    case p@ScParameterizedType(t1, _) =>
      extractDesignated(t1, withoutAliases) match {
        case Some((e, s)) => Some((e, s.followed(p.substitutor)))
        case None => None
      }
    case std@StdType(_, _) =>
      val asClass = std.asClass(DecompilerUtil.obtainProject)
      if (asClass.isEmpty) return None
      Some((asClass.get, ScSubstitutor.empty))
    case ScTypeParameterType(_, _, _, _, param) => Some(param, ScSubstitutor.empty)
    case _ => None
  }

  def isSingletonType(tp: ScType): Boolean = tp match {
    case _: ScThisType => true
    case ScDesignatorType(v) =>
      v match {
        case t: ScTypedDefinition => t.isStable
        case _ => false
      }
    case ScProjectionType(_, elem, _) =>
      elem match {
        case t: ScTypedDefinition => t.isStable
        case _ => false
      }
    case _ => false
  }

  def extractDesignatorSingletonType(tp: ScType): Option[ScType] = tp match {
    case ScDesignatorType(v) =>
      v match {
        case o: ScObject => None
        case p: ScParameter if p.isStable => p.getRealParameterType(TypingContext.empty).toOption
        case t: ScTypedDefinition if t.isStable => t.getType(TypingContext.empty).toOption
        case _ => None
      }
    case proj@ScProjectionType(_, elem, _) =>
      elem match {
        case o: ScObject => None
        case p: ScParameter if p.isStable => p.getRealParameterType(TypingContext.empty).toOption.map(proj.actualSubst.subst)
        case t: ScTypedDefinition if t.isStable => t.getType(TypingContext.empty).toOption.map(proj.actualSubst.subst)
        case _ => None
      }
    case _ => None
  }

  // TODO: Review this against SLS 3.2.1
  def isStable(t: ScType): Boolean = t match {
    case ScThisType(_) => true
    case ScProjectionType(projected, element: ScObject, _) => isStable(projected)
    case ScProjectionType(projected, element: ScTypedDefinition, _) => isStable(projected) && element.isStable
    case ScDesignatorType(o: ScObject) => true
    case ScDesignatorType(r: ScTypedDefinition) if r.isStable => true
    case _ => false
  }

  def projectionOption(tp: ScType): Option[ScType] = tp match {
    case ScParameterizedType(des, _) => projectionOption(des)
    case proj@ScProjectionType(p, elem, _) => proj.actualElement match {
      case c: PsiClass => Some(p)
      case t: ScTypeAliasDefinition =>
        projectionOption(proj.actualSubst.subst(t.aliasedType(TypingContext.empty).getOrElse(return None)))
      case t: ScTypeAliasDeclaration => Some(p)
      case _ => None
    }
    case ScDesignatorType(t: ScTypeAliasDefinition) =>
      projectionOption(t.aliasedType(TypingContext.empty).getOrElse(return None))
    case _ => None
  }

  /**
   * Expands type aliases, including those in a type projection. Type Alias Declarations are replaced by their upper
   * bound.
   *
   * @see http://youtrack.jetbrains.net/issue/SCL-2872
   */
  // TODO This is all a bit ad-hoc. What can we learn from scalac?
  // TODO perhaps we need to choose the lower bound if we are in a contravariant position. We get away
  //      with this as we currently only rely on this method to determine covariant types: the parameter
  //      types of FunctionN, or the elements of TupleN
  def expandAliases(tp: ScType): TypeResult[ScType] = tp match {
    case proj@ScProjectionType(p, elem, _) => proj.actualElement match {
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType(TypingContext.empty).flatMap(t => expandAliases(proj.actualSubst.subst(t)))
      case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty  =>
        t.upperBound.flatMap(upper => expandAliases(proj.actualSubst.subst(upper)))
      case _ => Success(tp, None)
    }
    case at: ScAbstractType => expandAliases(at.upper) // ugly hack for SCL-3592
    case ScDesignatorType(t: ScType) => expandAliases(t)
    case ScDesignatorType(ta: ScTypeAliasDefinition) => expandAliases(ta.aliasedType(TypingContext.empty).getOrNothing)
    case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty =>
      t.upperBound.flatMap(expandAliases)
    case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
      t.aliasedType(TypingContext.empty)
    case pt: ScParameterizedType if pt.isAliasType != None =>
      val aliasType: AliasType = pt.isAliasType.get
      aliasType.upper.flatMap(expandAliases)
    case _ => Success(tp, None)
  }

  @tailrec
  def removeAliasDefinitions(tp: ScType): ScType = {
    var updated = false
    val res = tp.recursiveUpdate { t =>
      t.isAliasType match {
        case Some(AliasType(ta, _, upper)) if ta.isInstanceOf[ScTypeAliasDefinition] =>
          updated = true
          (true, upper.getOrAny)
        case _ => (false, t)
      }
    }
    if (!updated) tp
    else removeAliasDefinitions(res)
  }

  /**
   * @return Some((designator, paramType, returnType)), or None
   */
  def extractPartialFunctionType(tp: ScType): Option[(ScType, ScType, ScType)] = expandAliases(tp).getOrAny match {
    case pt@ScParameterizedType(des, typeArgs) => pt.getPartialFunctionType
    case _ => None
  }
  
  /**
   * Unwraps the method type corresponding to the parameter secion at index `n`.
   *
   * For example:
   *
   * def foo(a: Int)(b: String): Boolean
   *
   * nested(foo.methodType(...), 1) => MethodType(retType = Boolean, params = Seq(String))
   */
  def nested(tpe: ScType, n: Int): Option[ScType] = {
    if (n == 0) Some(tpe)
    else tpe match {
      case mt: ScMethodType => nested(mt.returnType, n - 1)
      case _ => None
    }
  }

  /**
   * Creates a type that designates `element`. Usually this will be a ScDesignatorType, except for the
   * special case when `element` represent a standard type, such as scala.Double.
   *
   * @see http://youtrack.jetbrains.net/issue/SCL-2913
   */
  def designator(element: PsiNamedElement): ScType = {
    element match {
      case td: ScClass => StdType.QualNameToType.getOrElse(td.qualifiedName, new ScDesignatorType(element))
      case _ =>
        val clazzOpt = element match {
          case p: ScClassParameter => Option(p.containingClass)
          case _ => element.getContext match {
            case _: ScTemplateBody | _: ScEarlyDefinitions =>
              Option(ScalaPsiUtil.contextOfType(element, strict = true, classOf[ScTemplateDefinition]))
            case _ => None
          }
        }

        clazzOpt match {
          case Some(clazz) => ScProjectionType(ScThisType(clazz), element, superReference = false)
          case _ => new ScDesignatorType(element)
        }
    }
  }

  object ExtractClass {
    def unapply(aType: ScType) = ScType.extractClass(aType)
  }
}