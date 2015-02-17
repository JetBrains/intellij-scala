package org.jetbrains.plugins.scala.meta.trees


import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}

import scala.meta.internal.{ast=>m}

object TreeAdapter {

  def convert[T](sq: collection.mutable.Seq[T]): collection.immutable.Seq[T] =
    collection.immutable.Seq[T](sq:_*)

  def ideaToMeta(tree: ScalaPsiElement): m.Tree = {
    tree match {
      case t: p.statements.ScValueDeclaration =>
        m.Decl.Val(Nil, Seq(t.getIdList.fieldIds map { it => m.Pat.Var.Term(m.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: p.statements.ScVariableDeclaration =>
        m.Decl.Var(Nil, Seq(t.getIdList.fieldIds map { it => m.Pat.Var.Term(m.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: p.statements.ScTypeAliasDeclaration =>
        m.Decl.Type(Nil, m.Type.Name(t.name), Seq(t.typeParameters map {TypeAdapter(_)}:_*), TypeAdapter.typeBounds(t))
      case t: p.statements.ScFunctionDeclaration =>
        m.Decl.Def(convertMods(t), m.Term.Name(t.name), Seq(t.typeParameters map {TypeAdapter(_)}:_*), Seq(t.paramClauses.clauses.map(convertParams(_)):_*), returnType(t.typeElement))
      case _ => ???
    }
  }

  def convertMods(t: p.statements.ScFunctionDeclaration): Seq[m.Mod] = {
    Seq(
      if (t.isPrivate) Some(m.Mod.Private()) else None,
      if (t.isProtected) Some(m.Mod.Protected()) else None
    ).flatten
  }

  def convertParams(params: p.statements.params.ScParameterClause): Seq[Param] = {
      Seq(params.parameters.map {
        param =>
          if(param.isVarArgs)
           m.Term.Param(Nil, m.Term.Name(param.name),  param.typeElement.map(tp=>m.Type.Arg.Repeated(TypeAdapter(tp))), None)
          else
            m.Term.Param(Nil, m.Term.Name(param.name), param.typeElement.map(TypeAdapter(_)), None)
      }: _*)
  }

  def returnType(tp: Option[ScTypeElement]): m.Type = {
    tp match {
      case Some(t) => TypeAdapter(t)
      case None    => m.Type.Name("Unit")
    }
  }
}

object TypeAdapter {

  def apply(tp: p.base.types.ScTypeElement): m.Type = {

    tp match {
      case t: p.base.types.ScSimpleTypeElement =>
        m.Type.Name(t.calcType.canonicalText)
      case t: p.base.types.ScFunctionalTypeElement =>
        TypeAdapter(t.paramTypeElement) match {
          case m.Type.Tuple(elements) => m.Type.Function(elements, TypeAdapter(t.returnTypeElement.get))
          case param => m.Type.Function(Seq(param), TypeAdapter(t.returnTypeElement.get))
        }
      case t: p.base.types.ScParameterizedTypeElement =>
        m.Type.Apply(m.Type.Name(t.typeElement.calcType.canonicalText), Seq(t.typeArgList.typeArgs.map(TypeAdapter(_)): _*))
      case t: p.base.types.ScTupleTypeElement =>
        m.Type.Tuple(Seq(t.components.map(TypeAdapter(_)):_*))
      case _ => ???
    }
  }

  def apply(tp: ptype.ScType): m.Type = {

    tp match {
      case t: ptype.ScParameterizedType => m.Type.Apply(m.Type.Name(t.canonicalText), Seq(t.typeArgs.map(TypeAdapter(_)): _*))
      case t: ptype.ScType => m.Type.Name(t.canonicalText)

    }
  }

  def apply(tp: p.statements.params.ScTypeParam): m.Type.Param = {
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") m.Type.Name(tp.name) else m.Name.Anonymous(),
      Seq(tp.typeParameters.map(TypeAdapter(_)):_*),
      TypeAdapter.typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    )
  }

  def viewBounds(tp: p.toplevel.ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.viewTypeElement.map(TypeAdapter(_)):_*)
  }

  def contextBounds(tp: p.toplevel.ScTypeBoundsOwner): Seq[m.Type] = {
    Seq(tp.viewTypeElement.map(TypeAdapter(_)):_*)
  }

  def typeBounds(tp: p.toplevel.ScTypeBoundsOwner): m.Type.Bounds = {
    m.Type.Bounds(tp.lowerTypeElement.map(TypeAdapter(_)), tp.upperTypeElement.map(TypeAdapter(_)))
  }
}

