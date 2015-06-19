package org.jetbrains.plugins.scala.meta.trees

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, TypeResult}

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}
import scala.meta.internal.{ast=>m}
import scala.meta.internal.{semantic => h}

trait TypeAdapter {
  self: Converter =>

  def toType(tp: p.base.types.ScTypeElement): m.Type = {

    tp match {
      case t: p.base.types.ScSimpleTypeElement =>
        t.reference match {
          case Some(reference) =>
            reference.bind() match {
              case Some(result) => m.Type.Name(result.name)
              case None => m.Type.Placeholder(m.Type.Bounds(None, None))
            }
          case None =>  m.Type.Placeholder(m.Type.Bounds(None, None))
        }
      case t: p.base.types.ScFunctionalTypeElement =>
        toType(t.paramTypeElement) match {
          case m.Type.Tuple(elements) => m.Type.Function(elements, toType(t.returnTypeElement.get))
          case param => m.Type.Function(Seq(param), toType(t.returnTypeElement.get))
        }
      case t: p.base.types.ScParameterizedTypeElement =>
        m.Type.Apply(m.Type.Name(t.typeElement.calcType.canonicalText), t.typeArgList.typeArgs.toStream.map(toType))
      case t: p.base.types.ScTupleTypeElement =>
        m.Type.Tuple(Seq(t.components.map(toType):_*))
      case t: p.base.types.ScWildcardTypeElement =>
        m.Type.Placeholder(typeBounds(t))
      case t: p.base.types.ScParenthesisedTypeElement =>
        t.typeElement match {
          case Some(t: p.base.types.ScInfixTypeElement) => m.Type.ApplyInfix(toType(t.lOp), m.Type.Name(t.ref.refName), toType(t.rOp.get))
          case _ => ???
        }
      case t: p.base.types.ScTypeVariableTypeElement =>
        println("i cannot into type variables"); ???
      case _ => println(tp.getClass); ???
    }
  }

  def toType(tr: TypeResult[ptype.ScType]): m.Type = {
    import org.jetbrains.plugins.scala.lang.psi.types.result._
    tr match {
      case Success(res, _) => toType(res)
      case Failure(cause, place) => throw new RuntimeException(s"Failed to convert type: $cause at $place")
    }
  }

  def toType(elem: PsiElement): m.Type = {
    elem match {
      case t: p.toplevel.packaging.ScPackaging => m.Type.Singleton(toTermName(t.reference.get))
      case t: p.toplevel.typedef.ScTemplateDefinition => toType(t.getType(TypingContext.empty))
    }
  }

  def toType(tp: ptype.ScType): m.Type = {

    tp match {
      case t: ptype.ScParameterizedType => m.Type.Apply(toType(t.designator), Seq(t.typeArgs.map(toType):_*))
      case t: ptype.ScDesignatorType =>  m.Type.Name(t.canonicalText, denot = h.Denotation.Zero).withDenot(t.element)

      case t: ptype.ScType => m.Type.Name(t.canonicalText)
    }
  }

  def toType(tp: p.statements.params.ScTypeParam): m.Type.Param = {
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") m.Type.Name(tp.name) else m.Name.Anonymous(),
      Seq(tp.typeParameters.map(toType):_*),
      typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    )
  }

  def viewBounds(tp: p.toplevel.ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.viewTypeElement.map(toType):_*)
  }

  def contextBounds(tp: p.toplevel.ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.contextBoundTypeElement.map(toType):_*)
  }

  def typeBounds(tp: p.toplevel.ScTypeBoundsOwner): m.Type.Bounds = {
    m.Type.Bounds(tp.lowerTypeElement.map(toType), tp.upperTypeElement.map(toType))
  }

  def returnType(tr: ptype.result.TypeResult[ptype.ScType]): m.Type = {
    import ptype.result._
    tr match {
      case Success(t, elem) => toType(t)
      case Failure(cause, place)    => m.Type.Name("Unit")
    }
  }
}
