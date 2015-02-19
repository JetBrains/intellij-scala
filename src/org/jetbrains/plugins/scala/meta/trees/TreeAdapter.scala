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
        m.Decl.Val(Nil, Stream(t.getIdList.fieldIds map { it => m.Pat.Var.Term(m.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: p.statements.ScVariableDeclaration =>
        m.Decl.Var(Nil, Stream(t.getIdList.fieldIds map { it => m.Pat.Var.Term(m.Term.Name(it.name))}: _*), TypeAdapter(t.typeElement.get.calcType))
      case t: p.statements.ScTypeAliasDeclaration =>
        m.Decl.Type(Nil, m.Type.Name(t.name), Stream(t.typeParameters map {TypeAdapter(_)}:_*), TypeAdapter.typeBounds(t))
      case t: p.statements.ScFunctionDeclaration =>
        m.Decl.Def(convertMods(t), m.Term.Name(t.name), Stream(t.typeParameters map {TypeAdapter(_)}:_*), Stream(t.paramClauses.clauses.map(convertParams):_*), returnType(t.typeElement))
      case t: p.statements.ScPatternDefinition =>
        patternDefinition(t)
      case t: p.statements.ScVariableDefinition =>
        def pattern(bp: p.base.patterns.ScBindingPattern) = m.Pat.Var.Term(m.Term.Name(bp.name))
        m.Defn.Var(convertMods(t), Stream(t.bindings.map(pattern):_*), t.declaredType.map(TypeAdapter(_)), expression(t.expr))
      case other => println(other.getClass); ???
    }
  }

  def expression(tree: Option[p.expr.ScExpression]): Option[m.Term] = {
    tree match {
      case Some(t: p.base.ScLiteral) => Some(literal(t))
      case Some(t: p.expr.ScUnderscoreSection) => None
      case None => None
      case Some(other) => println(other.getClass); ???
    }
  }

  def literal(l: p.base.ScLiteral): m.Lit = {
    import p.base.ScLiteral
    import m.Lit
    l match {
      case ScLiteral(i: Integer)              => Lit.Int(i)
      case ScLiteral(l: java.lang.Long)       => Lit.Long(l)
      case ScLiteral(f: java.lang.Float)      => Lit.Float(f)
      case ScLiteral(d: java.lang.Double)     => Lit.Double(d)
      case ScLiteral(b: java.lang.Boolean)    => Lit.Bool(b)
      case ScLiteral(c: java.lang.Character)  => Lit.Char(c)
      case ScLiteral(s: String)               => Lit.String(s)
      case other => println(other.getClass); ???
    }
  }

  def patternDefinition(t: p.statements.ScPatternDefinition): m.Tree = {
    def pattern(bp: p.base.patterns.ScBindingPattern): m.Pat = {
      m.Pat.Var.Term(m.Term.Name(bp.name))
    }

    if(t.bindings.exists(_.isVal))
      m.Defn.Val(convertMods(t), Stream(t.bindings.map(pattern):_*), t.declaredType.map(TypeAdapter(_)), expression(t.expr).get)
    else if(t.bindings.exists(_.isVar))
      m.Defn.Var(convertMods(t), Stream(t.bindings.map(pattern):_*), t.declaredType.map(TypeAdapter(_)), expression(t.expr))
    else ???
  }

  def convertMods(t: p.toplevel.ScModifierListOwner): Seq[m.Mod] = {
    Stream(
      if (t.hasModifierProperty("private")) Some(m.Mod.Private()) else None,
      if (t.hasModifierProperty("protected")) Some(m.Mod.Protected()) else None
    ).flatten
  }

  def convertParams(params: p.statements.params.ScParameterClause): Seq[Param] = {
    Stream(params.parameters.map {
        param =>
          if(param.isVarArgs)
           m.Term.Param(convertMods(param), m.Term.Name(param.name),  param.typeElement.map(tp=>m.Type.Arg.Repeated(TypeAdapter(tp))), None)
          else
            m.Term.Param(convertMods(param), m.Term.Name(param.name), param.typeElement.map(TypeAdapter(_)), None)
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
        m.Type.Apply(m.Type.Name(t.typeElement.calcType.canonicalText), Stream(t.typeArgList.typeArgs.map(TypeAdapter(_)): _*))
      case t: p.base.types.ScTupleTypeElement =>
        m.Type.Tuple(Seq(t.components.map(TypeAdapter(_)):_*))
      case _ => ???
    }
  }

  def apply(tp: ptype.ScType): m.Type = {

    tp match {
      case t: ptype.ScParameterizedType => m.Type.Apply(m.Type.Name(t.canonicalText), Stream(t.typeArgs.map(TypeAdapter(_)): _*))
      case t: ptype.ScType => m.Type.Name(t.canonicalText)

    }
  }

  def apply(tp: p.statements.params.ScTypeParam): m.Type.Param = {
    m.Type.Param(
      if(tp.isCovariant) m.Mod.Covariant() :: Nil else if(tp.isContravariant) m.Mod.Contravariant() :: Nil else Nil,
      if (tp.name != "_") m.Type.Name(tp.name) else m.Name.Anonymous(),
      Stream(tp.typeParameters.map(TypeAdapter(_)):_*),
      TypeAdapter.typeBounds(tp),
      viewBounds(tp),
      contextBounds(tp)
    )
  }

  def viewBounds(tp: p.toplevel.ScTypeBoundsOwner): Seq[m.Type] = {
    Stream(tp.viewTypeElement.map(TypeAdapter(_)):_*)
  }

  def contextBounds(tp: p.toplevel.ScTypeBoundsOwner): Seq[m.Type] = {
    Stream(tp.viewTypeElement.map(TypeAdapter(_)):_*)
  }

  def typeBounds(tp: p.toplevel.ScTypeBoundsOwner): m.Type.Bounds = {
    m.Type.Bounds(tp.lowerTypeElement.map(TypeAdapter(_)), tp.upperTypeElement.map(TypeAdapter(_)))
  }
}

