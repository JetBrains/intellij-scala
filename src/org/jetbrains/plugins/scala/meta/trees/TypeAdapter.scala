package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}

import scala.collection.immutable.Seq
import scala.language.postfixOps
import scala.meta.internal.{ast => m, semantic => h}
import org.scalameta.collections._
import scala.{Seq => _}

trait TypeAdapter {
  self: TreeConverter =>

  private val typeCache = TwoWayCache[ptype.ScType, m.Type]()
  private val typeElementCache = TwoWayCache[ScTypeElement, m.Type]()
  private val psiElementTypeChache = TwoWayCache[PsiElement, m.Type]()

  def toType(tp: ScTypeElement): m.Type = {
    typeElementCache.getOrElseUpdate(tp, {
      tp match {
        case t: ScSimpleTypeElement =>
          t.reference match {
            case Some(reference) =>
              reference.bind() match {
                case Some(result) => toTypeName(result.element)
                case None => m.Type.Placeholder(m.Type.Bounds(None, None))
              }
            case None => m.Type.Placeholder(m.Type.Bounds(None, None))
          }
        case t: ScFunctionalTypeElement =>
          toType(t.paramTypeElement) match {
            case m.Type.Tuple(elements) => m.Type.Function(elements, toType(t.returnTypeElement.get))
            case param => m.Type.Function(Seq(param), toType(t.returnTypeElement.get))
          }
        case t: ScParameterizedTypeElement =>
          m.Type.Apply(toType(t.typeElement.calcType), t.typeArgList.typeArgs.toStream.map(toType))
        case t: ScTupleTypeElement =>
          m.Type.Tuple(Seq(t.components.map(toType): _*))
        case t: ScWildcardTypeElement =>
          m.Type.Placeholder(typeBounds(t))
        case t: ScParenthesisedTypeElement =>
          t.typeElement match {
            case Some(t: ScInfixTypeElement) => m.Type.ApplyInfix(toType(t.lOp), toTypeName(t.ref), toType(t.rOp.get))
            case _ => unreachable
          }
        case t: ScTypeVariableTypeElement => throw new ScalaMetaException("i cannot into type variables")
        case other => other ?!
      }
    })
  }

  def toType(tr: TypeResult[ptype.ScType]): m.Type = {
    import org.jetbrains.plugins.scala.lang.psi.types.result._
    tr match {
      case Success(res, _) => toType(res)
      case Failure(cause, place) => throw new ScalaMetaTypeResultFailure(place, cause)
    }
  }

  def toType(elem: PsiElement): m.Type = {
    psiElementTypeChache.getOrElseUpdate(elem, {
      elem match {
        case t: typedef.ScTemplateDefinition =>
          val s = new ScSubstitutor(ScSubstitutor.cache.toMap, Map(), None)
          toType(s.subst(t.getType(TypingContext.empty).get)) // FIXME: what about typing context?
        case t: packaging.ScPackaging => m.Type.Singleton(toTermName(t.reference.get))
        case t: ScFunction => m.Type.Function(Seq(t.paramTypes.map(toType(_).asInstanceOf[m.Type.Arg]): _*), toType(t.returnType))
        case t: PsiPackage if t.getName == null => m.Type.Singleton(rootPackageName)
        case t: PsiPackage => m.Type.Singleton(toTermName(t))
        case t: PsiClass => m.Type.Name(t.getName).withDenot(t)
        case other => other ?!
      }
    })
  }

  def toType(tp: ptype.ScType): m.Type = {
    typeCache.getOrElseUpdate(tp, {
      tp match {
        case t: ptype.ScParameterizedType =>
          m.Type.Apply(toType(t.designator), Seq(t.typeArgs.map(toType): _*))
        case t: ptype.ScThisType =>
          toTypeName(t.clazz)
        case t: ptype.ScProjectionType =>
          t.projected match {
            case tt: ptype.ScThisType =>
              m.Type.Select(toTermName(tt.clazz), toTypeName(t.actualElement))
            case _ =>
              m.Type.Project(toType(t.projected), toTypeName(t.actualElement))
          }
        case t: ptype.ScDesignatorType =>
          toTypeName(t.element)
        case t: ptype.StdType =>
          toTypeName(t)
        case t: ptype.ScType =>
          LOG.warn(s"Unknown type: ${t.getClass} - ${t.canonicalText}")
          m.Type.Name(t.canonicalText)
      }
    })
  }

  def toTypeParams(tp: p.statements.params.ScTypeParam): m.Type.Param = {
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") m.Type.Name(tp.name) else m.Name.Anonymous(),
      Seq(tp.typeParameters.map(toTypeParams):_*),
      typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    )
  }

  def toTypeParams(tp: PsiTypeParameter): m.Type.Param = {
    m.Type.Param(
      m.Mod.Covariant() :: Nil,
      m.Type.Name(tp.getName),
      Seq(tp.getTypeParameters.map(toTypeParams):_*),
      m.Type.Bounds(None, None),
      Seq.empty, Seq.empty
    )
  }

  def viewBounds(tp: ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.viewTypeElement.map(toType):_*)
  }

  def contextBounds(tp: ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.contextBoundTypeElement.map(toType):_*)
  }

  def typeBounds(tp: ScTypeBoundsOwner): m.Type.Bounds = {
    m.Type.Bounds(tp.lowerTypeElement.map(toType), tp.upperTypeElement.map(toType))
  }

  def returnType(tr: ptype.result.TypeResult[ptype.ScType]): m.Type = {
    import ptype.result._
    tr match {
      case Success(t, elem) => toType(t)
      case Failure(cause, place) =>
        LOG.warn(s"Failed to infer return type($cause) at ${place.map(_.getText).getOrElse("UNKNOWN")}")
        m.Type.Name("Unit")
    }
  }
}
