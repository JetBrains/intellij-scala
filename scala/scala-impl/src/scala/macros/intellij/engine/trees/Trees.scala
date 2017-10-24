package scala.macros.intellij.engine.trees

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

trait Trees extends scala.macros.trees.Trees { self: scala.macros.Universe =>
  override type Tree = ScalaPsiElement
  override type Ref = ScReferenceElement
//  override type Stat = this.type
//  override type Name = this.type
//  override type Lit = this.type
//  override type Term = this.type
//  override type Type = this.type
//  override type Pat = this.type
//  override type Member = this.type
//  override type Decl = this.type
//  override type Defn = this.type
//  override type Pkg = this.type
//  override type Ctor = this.type
//  override type Init = this.type
//  override type Self = this.type
//  override type Template = this.type
//  override type Mod = this.type
//  override type Enumerator = this.type
//  override type Import = this.type
//  override type Importer = this.type
//  override type Importee = this.type
//  override type Case = this.type
//  override type Source = this.type
}
