package scala.meta.trees

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScTypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.{api => p, types => ptype}
import org.scalameta.collections._

import scala.collection.immutable.Seq
import scala.language.postfixOps
import scala.meta.internal.{ast => m, semantic => h}
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
          val s = new ScSubstitutor(ScSubstitutor.cache.toMap, Map(), None)
          toType(s.subst(t.getType().get))
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
        case t: packaging.ScPackaging =>
          m.Type.Singleton(toTermName(t.reference.get)).setTypechecked
        case t: ScConstructor =>
          m.Type.Method(toParams(Seq(t.arguments:_*)), toType(t.newTemplate.get.getType(TypingContext.empty))).setTypechecked
        case t: ScPrimaryConstructor =>
          m.Type.Method(Seq(t.parameterList.clauses.map(convertParamClause):_*), toType(t.containingClass)).setTypechecked
        case t: ScFunctionDefinition =>
          m.Type.Method(Seq(t.parameterList.clauses.map(convertParamClause):_*), toType(t.getTypeWithCachedSubst)).setTypechecked
        case t: ScFunction =>
          m.Type.Function(Seq(t.paramTypes.map(toType(_).asInstanceOf[m.Type.Arg]): _*), toType(t.returnType)).setTypechecked
        case t: ScParameter =>
          val s = new ScSubstitutor(ScSubstitutor.cache.toMap, Map(), None)
          toType(s.subst(t.typeElement.get.getType().get))
        case t: ScTypedDefinition =>
          t.getTypeWithCachedSubst match {
            case Success(res, place) => toType(res)
            case Failure(cause, place) => unreachable
          }
        case t: ScReferenceElement =>
          t.bind() match {
            case Some(result) => toType(result.element)
            case None => m.Type.Placeholder(m.Type.Bounds(None, None))
          }
        case t: PsiPackage if t.getName == null =>
          m.Type.Singleton(std.rootPackageName).setTypechecked
        case t: PsiPackage =>
          m.Type.Singleton(toTermName(t)).setTypechecked
        case t: PsiClass =>
          m.Type.Name(t.getName).withAttrsFor(t).setTypechecked
        case other => other ?!
      }
    })
  }

  def toType(tp: ptype.ScType): m.Type = {
    typeCache.getOrElseUpdate(tp, {
      tp match {
        case t: ptype.ScParameterizedType =>
          m.Type.Apply(toType(t.designator), Seq(t.typeArgs.map(toType): _*)).setTypechecked
        case t: ptype.ScThisType =>
          toTypeName(t.clazz).setTypechecked
        case t: ptype.ScProjectionType =>
          t.projected match {
            case tt: ptype.ScThisType =>
              m.Type.Select(toTermName(tt.clazz), toTypeName(t.actualElement)).setTypechecked
            case _ =>
              m.Type.Project(toType(t.projected), toTypeName(t.actualElement)).setTypechecked
          }
        case t: ptype.ScDesignatorType =>
          if (t.element.isSingletonType)
            toSingletonType(t.element)
          else
            toTypeName(t.element)
        case t: ptype.StdType =>
          toStdTypeName(t)
        case t: ScTypeParameterType =>
          m.Type.Name(t.name).withAttrsFor(t.param).setTypechecked
        case t: ptype.ScType =>
          LOG.warn(s"Unknown type: ${t.getClass} - ${t.canonicalText}")
          m.Type.Name(t.canonicalText).withAttrs(h.Denotation.Zero)
      }
    })
  }

  def toSingletonType(elem: PsiElement): m.Type.Singleton = {
    m.Type.Singleton(toTermName(elem)).setTypechecked
  }

  def toTypeParams(tp: p.statements.params.ScTypeParam): m.Type.Param = {
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") toTypeName(tp) else m.Name.Anonymous().withAttrsFor(tp).setTypechecked,
      Seq(tp.typeParameters.map(toTypeParams):_*),
      typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    ).setTypechecked
  }

  def toTypeParams(tp: PsiTypeParameter): m.Type.Param = {
    m.Type.Param(
      m.Mod.Covariant() :: Nil,
      toTypeName(tp),
      Seq(tp.getTypeParameters.map(toTypeParams):_*),
      m.Type.Bounds(None, None),
      Seq.empty, Seq.empty
    ).setTypechecked
  }

  def viewBounds(tp: ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.viewTypeElement.map(toType):_*)
  }

  def contextBounds(tp: ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.contextBoundTypeElement.map(toType):_*)
  }

  def typeBounds(tp: ScTypeBoundsOwner): m.Type.Bounds = {
    m.Type.Bounds(tp.lowerTypeElement.map(toType), tp.upperTypeElement.map(toType)).setTypechecked
  }

  def returnType(tr: ptype.result.TypeResult[ptype.ScType]): m.Type = {
    import ptype.result._
    tr match {
      case Success(t, elem) => toType(t)
      case Failure(cause, place) =>
        LOG.warn(s"Failed to infer return type($cause) at ${place.map(_.getText).getOrElse("UNKNOWN")}")
        m.Type.Name("Unit").setTypechecked
    }
  }
}
