package org.jetbrains.plugins.scala.meta.trees


import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => psi}
import org.jetbrains.plugins.scala.lang.psi.{types => psitype}

import scala.meta.internal.{ast=>metast}

object TreeAdapter {

  def ideaToMeta(tree: ScalaPsiElement): metast.Tree = {
    tree match {
      case t: psi.statements.ScValueDeclaration =>
        metast.Decl.Val(Nil, Seq(t.getIdList.fieldIds map { it => metast.Pat.Var.Term(metast.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: psi.statements.ScVariableDeclaration =>
        metast.Decl.Var(Nil, Seq(t.getIdList.fieldIds map { it => metast.Pat.Var.Term(metast.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: psi.statements.ScTypeAliasDeclaration =>
        metast.Decl.Type(Nil, metast.Type.Name(t.name), Seq(t.typeParameters map {TypeAdapter(_)}:_*), TypeAdapter.typeBounds(t))
      case _ => ???
    }
  }
}

object TypeAdapter {

  def apply(tp: psi.base.types.ScTypeElement): metast.Type = {
    tp match {
      case t: psi.base.types.ScSimpleTypeElement => metast.Type.Name(t.calcType.canonicalText)
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
      contextBounds(tp),
      viewBounds(tp),
      TypeAdapter.typeBounds(tp)
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