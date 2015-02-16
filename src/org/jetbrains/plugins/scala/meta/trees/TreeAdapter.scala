package org.jetbrains.plugins.scala.meta.trees


import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => psi}
import org.jetbrains.plugins.scala.lang.psi.{types => psitype}

import scala.meta.internal.{ast=>metast}

object TreeAdapter {

  def convert[T](sq: collection.mutable.Seq[T]): collection.immutable.Seq[T] =
    collection.immutable.Seq[T](sq:_*)

  def ideaToMeta(tree: ScalaPsiElement): metast.Tree = {
    tree match {
      case t: psi.statements.ScValueDeclaration =>
        metast.Decl.Val(Nil, Seq(t.getIdList.fieldIds map { it => metast.Pat.Var.Term(metast.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: psi.statements.ScVariableDeclaration =>
        metast.Decl.Var(Nil, Seq(t.getIdList.fieldIds map { it => metast.Pat.Var.Term(metast.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: psi.statements.ScTypeAliasDeclaration =>
        metast.Decl.Type(Nil, metast.Type.Name(t.name), Seq(t.typeParameters map {TypeAdapter(_)}:_*), TypeAdapter.typeBounds(t))
      case t: psi.statements.ScFunctionDeclaration =>
        metast.Decl.Def(convertMods(t), metast.Term.Name(t.name), Seq(t.typeParameters map {TypeAdapter(_)}:_*), Seq(t.paramClauses.clauses.map(convertParams(_)):_*), returnType(t.typeElement))
      case _ => ???
    }
  }

  def convertMods(t: psi.statements.ScFunctionDeclaration): Seq[metast.Mod] = {
    Seq(
      if (t.isPrivate) Some(metast.Mod.Private()) else None,
      if (t.isProtected) Some(metast.Mod.Protected()) else None
    ).flatten
  }

  def convertParams(params: psi.statements.params.ScParameterClause): Seq[Param] = {
      Seq(params.parameters.map {
        param => metast.Term.Param(Nil, metast.Term.Name(param.name), param.typeElement.map(TypeAdapter(_)), None)
      }: _*)
  }

  def returnType(tp: Option[ScTypeElement]): metast.Type = {
    tp match {
      case Some(t) => TypeAdapter(t)
      case None    => metast.Type.Name("Unit")
    }
  }
}

object TypeAdapter {

  def apply(tp: psi.base.types.ScTypeElement): metast.Type = {
    tp match {
      case t: psi.base.types.ScSimpleTypeElement =>
        metast.Type.Name(t.calcType.canonicalText)
      case t: psi.base.types.ScParameterizedTypeElement =>
        metast.Type.Apply(metast.Type.Name(t.typeElement.calcType.canonicalText), Seq(t.typeArgList.typeArgs.map(TypeAdapter(_)): _*))
      case _ => ???
    }
  }

  def apply(tp: psitype.ScType): metast.Type = {

    tp match {
      case t: psitype.ScParameterizedType => metast.Type.Apply(metast.Type.Name(t.canonicalText), Seq(t.typeArgs.map(TypeAdapter(_)): _*))
      case t: psitype.ScType => metast.Type.Name(t.canonicalText)

    }
  }

  def apply(tp: psi.statements.params.ScTypeParam): metast.Type.Param = {
    metast.Type.Param(
      if(tp.isCovariant) metast.Mod.Covariant() :: Nil else if(tp.isContravariant) metast.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") metast.Type.Name(tp.name) else metast.Name.Anonymous(),
      Seq(tp.typeParameters.map(TypeAdapter(_)):_*),
      TypeAdapter.typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    )
  }

  def viewBounds(tp: psi.toplevel.ScTypeBoundsOwner): Seq[metast.Type] = {
    Seq(tp.viewTypeElement.map(TypeAdapter(_)):_*)
  }

  def contextBounds(tp: psi.toplevel.ScTypeBoundsOwner): Seq[metast.Type] = {
    Seq(tp.viewTypeElement.map(TypeAdapter(_)):_*)
  }

  def typeBounds(tp: psi.toplevel.ScTypeBoundsOwner): metast.Type.Bounds = {
    metast.Type.Bounds(tp.lowerTypeElement.map(TypeAdapter(_)), tp.upperTypeElement.map(TypeAdapter(_)))
  }
}

