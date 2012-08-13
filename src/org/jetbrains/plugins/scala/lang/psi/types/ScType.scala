package org.jetbrains.plugins.scala
package lang
package psi
package types

import decompiler.DecompilerUtil
import com.intellij.psi._
import com.intellij.openapi.project.Project
import api.statements._
import api.toplevel.ScTypedDefinition
import nonvalue.{ScMethodType, NonValueType}
import result.{Success, TypeResult, TypingContext}
import java.lang.Exception
import collection.mutable.HashSet
import collection.immutable.HashMap
import api.toplevel.templates.ScTemplateBody
import util.PsiTreeUtil
import api.toplevel.typedef.{ScTemplateDefinition, ScClass, ScObject}

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
 case ScCompoundType(components, decls, typeDecls, subst) =>
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
  final def equiv(t: ScType): Boolean = Equivalence.equiv(this, t)

  /**
   * Checks, whether the following assignment is correct:
   * val x: t = (y: this)
   */
  final def conforms(t: ScType, checkWeak: Boolean = false): Boolean = Conformance.conforms(t, this, checkWeak)

  final def weakConforms(t: ScType): Boolean = Conformance.conforms(t, this, true)

  final def presentableText = ScType.presentableText(this)

  final def canonicalText = ScType.canonicalText(this)

  override def toString = presentableText

  def isValue: Boolean

  final def isStable: Boolean = ScType.isStable(this)

  def inferValueType: ValueType

  /**
   * This method is important for parameters expected type.
   * There shouldn't be any abstract type in this expected type.
   * todo rewrite with recursiveUpdate method
   */
  def removeAbstracts = this

  def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    (false, uSubst)
  }

  class RecursiveUpdateException extends Exception {
    override def getMessage: String = "Type mismatch after update method"
  }

  import collection.immutable.{HashSet => IHashSet}

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
    val res = update(this, variance)
    if (res._1) res._2
    else this
  }

  def collectAbstracts: Seq[ScAbstractType] = {
    val set: HashSet[ScAbstractType] = new HashSet[ScAbstractType]

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

  def extractClass2(t: ScType, project: Project = null): Option[PsiClass] = {
    t match {
      case p@ScParameterizedType(t1, _) => extractClass2(t1, project)
      case _ => extractClassType(t, Some(project)).map(_._1)
    }
  }

  def extractClassType(t: ScType, project: Option[Project] = None): Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case n: NonValueType => extractClassType(n.inferValueType)
    case ScDesignatorType(clazz: PsiClass) => Some(clazz, ScSubstitutor.empty)
    case ScDesignatorType(ta: ScTypeAliasDefinition) =>
      val result = ta.aliasedType(TypingContext.empty)
      if (result.isEmpty) return None
      extractClassType(result.get)
    case proj@ScProjectionType(p, elem, subst, _) => proj.actualElement match {
      case c: PsiClass => Some((c, proj.actualSubst))
      case t: ScTypeAliasDefinition =>
        val result = t.aliasedType(TypingContext.empty)
        if (result.isEmpty) return None
        extractClassType(proj.actualSubst.subst(result.get))
      case _ => None
    }
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    }
    case tuple@ScTupleType(comp) => {
      tuple.resolveTupleTrait match {
        case Some(clazz) => extractClassType(clazz)
        case _ => None
      }
    }
    case fun: ScFunctionType => {
      fun.resolveFunctionTrait match {
        case Some(tp) => extractClassType(tp)
        case _ => None
      }
    }
    case std@StdType(_, _) =>
      val asClass = std.asClass(project.getOrElse(DecompilerUtil.obtainProject))
      if (asClass.isEmpty) return None
      Some((asClass.get, ScSubstitutor.empty))
    case _ => None
  }

  def extractDesignated(t: ScType): Option[Pair[PsiNamedElement, ScSubstitutor]] = t match {
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case ScThisType(c) => Some(c, ScSubstitutor.empty)
    case proj@ScProjectionType(p, e, s, _) => Some((proj.actualElement, proj.actualSubst))
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((e, s)) => Some((e, s.followed(p.substitutor)))
        case None => None
      }
    }
    case _ => None
  }

  def isSingletonType(tp: ScType): Boolean = tp match {
    case _: ScThisType => true
    case ScDesignatorType(v) =>
      v match {
        case t: ScTypedDefinition => t.isStable
        case _ => false
      }
    case ScProjectionType(_, elem, _, _) =>
      elem match {
        case t: ScTypedDefinition => t.isStable
        case _ => false
      }
    case _ => false
  }

  // TODO: Review this against SLS 3.2.1
  def isStable(t: ScType): Boolean = t match {
    case ScThisType(_) => true
    case ScProjectionType(projected, element: ScObject, _, _) => isStable(projected)
    case ScProjectionType(projected, element: ScTypedDefinition, _, _) => isStable(projected) && element.isStable
    case ScDesignatorType(o: ScObject) => true
    case ScDesignatorType(r: ScTypedDefinition) if r.isStable => true
    case _ => false
  }

  def projectionOption(tp: ScType): Option[ScType] = tp match {
    case ScParameterizedType(des, _) => projectionOption(des)
    case proj@ScProjectionType(p, elem, subst, _) => proj.actualElement match {
      case c: PsiClass => Some(p)
      case t: ScTypeAliasDefinition =>
        projectionOption(proj.actualSubst.subst(t.aliasedType(TypingContext.empty).getOrElse(return None)))
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
    case proj@ScProjectionType(p, elem, subst, _) => proj.actualElement match {
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType(TypingContext.empty).flatMap(t => expandAliases(proj.actualSubst.subst(t)))
      case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty  =>
        t.upperBound.flatMap(upper => expandAliases(proj.actualSubst.subst(upper)))
      case _ => Success(tp, None)
    }
    case at: ScAbstractType => Success(at.upper, None) // ugly hack for SCL-3592
    case ScDesignatorType(t: ScType) => expandAliases(t)
    case ScDesignatorType(ta: ScTypeAliasDefinition) => expandAliases(ta.aliasedType(TypingContext.empty).getOrNothing)
    case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty =>
      t.upperBound.flatMap(expandAliases)
    case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
      t.aliasedType(TypingContext.empty)
    case pt: ScParameterizedType if Conformance.isAliasType(pt) != None =>
      val expandedDesignator = expandAliases(pt.designator)
      val expandedTypeArgsResult: TypeResult[Seq[ScType]] = TypeResult.sequence(pt.typeArgs.map(expandAliases))
      TypeResult.ap2(expandedDesignator, expandedTypeArgsResult) {
        ScParameterizedType(_, _)
      }

    case tp => Success(tp, None)
  }

  def extractTupleType(tp: ScType): Option[ScTupleType] = expandAliases(tp).getOrAny match {
    case tt: ScTupleType => Some(tt)
    case pt: ScParameterizedType => pt.getTupleType
    case _ => None
  }

  def extractFunctionType(tp: ScType): Option[ScFunctionType] = expandAliases(tp).getOrAny match {
    case ft: ScFunctionType => Some(ft)
    case pt: ScParameterizedType =>
      pt.getFunctionType
    case _ => None
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
        element.getParent match {
          case td: ScTemplateBody =>
            val clazz = PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition], true)
            new ScProjectionType(ScThisType(clazz), element, ScSubstitutor.empty, false)
          case _ =>
            new ScDesignatorType(element)
        }
    }
  }

  object ExtractClass {
    def unapply(aType: ScType) = ScType.extractClass(aType)
  }
}