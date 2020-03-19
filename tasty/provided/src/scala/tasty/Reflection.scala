package scala.tasty

import scala.quoted.show.SyntaxHighlight
import scala.tasty.reflect._

// Reproduces the ABI of https://github.com/lampepfl/dotty/blob/0.22.0-RC1/library/src/scala/tasty/Reflection.scala
// Requires the corresponding "compat" class to reproduce the API.
class Reflection(private[scala] val internal: CompilerInterface) { self =>

  // Core

  type Context = internal.Context
  type Settings = internal.Settings
  type Tree = internal.Tree
  type PackageClause = internal.PackageClause
  type Statement = internal.Statement
  type Import = internal.Import
  type Definition = internal.Definition
  type PackageDef = internal.PackageDef
  type ClassDef = internal.ClassDef
  type TypeDef = internal.TypeDef
  type DefDef = internal.DefDef
  type ValDef = internal.ValDef
  type Term = internal.Term
  type Ref = internal.Ref
  type Ident = internal.Ident
  type Select = internal.Select
  type Literal = internal.Literal
  type This = internal.This
  type New = internal.New
  type NamedArg = internal.NamedArg
  type Apply = internal.Apply
  type TypeApply = internal.TypeApply
  type Super = internal.Super
  type Typed = internal.Typed
  type Assign = internal.Assign
  type Block = internal.Block
  type Closure = internal.Closure
  type If = internal.If
  type Match = internal.Match
  type GivenMatch = internal.GivenMatch
  type Try = internal.Try
  type Return = internal.Return
  type Repeated = internal.Repeated
  type Inlined = internal.Inlined
  type SelectOuter = internal.SelectOuter
  type While = internal.While
  type TypeTree = internal.TypeTree
  type Inferred = internal.Inferred
  type TypeIdent = internal.TypeIdent
  type TypeSelect = internal.TypeSelect
  type Projection = internal.Projection
  type Singleton = internal.Singleton
  type Refined = internal.Refined
  type Applied = internal.Applied
  type Annotated = internal.Annotated
  type MatchTypeTree = internal.MatchTypeTree
  type ByName = internal.ByName
  type LambdaTypeTree = internal.LambdaTypeTree
  type TypeBind = internal.TypeBind
  type TypeBlock = internal.TypeBlock
  type TypeBoundsTree = internal.TypeBoundsTree
  type WildcardTypeTree = internal.WildcardTypeTree
  type CaseDef = internal.CaseDef
  type TypeCaseDef = internal.TypeCaseDef
  type Bind = internal.Bind
  type Unapply = internal.Unapply
  type Alternatives = internal.Alternatives
  type TypeOrBounds = internal.TypeOrBounds
  type NoPrefix = internal.NoPrefix
  type TypeBounds = internal.TypeBounds
  type Type = internal.Type
  type ConstantType = internal.ConstantType
  type TermRef = internal.TermRef
  type TypeRef = internal.TypeRef
  type SuperType = internal.SuperType
  type Refinement = internal.Refinement
  type AppliedType = internal.AppliedType
  type AnnotatedType = internal.AnnotatedType
  type AndType = internal.AndType
  type OrType = internal.OrType
  type MatchType = internal.MatchType
  type ByNameType = internal.ByNameType
  type ParamRef = internal.ParamRef
  type ThisType = internal.ThisType
  type RecursiveThis = internal.RecursiveThis
  type RecursiveType = internal.RecursiveType
  type LambdaType[ParamInfo] = internal.LambdaType[ParamInfo]
  type MethodType = internal.MethodType
  type PolyType = internal.PolyType
  type TypeLambda = internal.TypeLambda
  type ImportSelector = internal.ImportSelector
  type SimpleSelector = internal.SimpleSelector
  type RenameSelector = internal.RenameSelector
  type OmitSelector = internal.OmitSelector
  type Id = internal.Id
  type Signature = internal.Signature
  type Position = internal.Position
  type SourceFile = internal.SourceFile
  type Comment = internal.Comment
  type Constant = internal.Constant
  type Symbol = internal.Symbol
  type Flags = internal.Flags
  type ImplicitSearchResult = internal.ImplicitSearchResult
  type ImplicitSearchSuccess = internal.ImplicitSearchSuccess
  type ImplicitSearchFailure = internal.ImplicitSearchFailure
  type DivergingImplicit = internal.DivergingImplicit
  type NoMatchingImplicits = internal.NoMatchingImplicits
  type AmbiguousImplicits = internal.AmbiguousImplicits

  // Contexts

  def rootContext: Context = ???

  object ContextOps {
    def owner(self: Context): Symbol = ???
    def source(self: Context): java.nio.file.Path = ???
    def requiredPackage(self: Context, path: String): Symbol = ???
    def requiredClass(self: Context, path: String): Symbol = ???
    def requiredModule(self: Context, path: String): Symbol = ???
    def requiredMethod(self: Context, path: String): Symbol = ???
  }

  // Trees

  object TreeOps {
    def pos(self: Tree)(implicit ctx: Context): Position = ???
    def symbol(self: Tree)(implicit ctx: Context): Symbol = ???
    def showExtractors(self: Tree)(implicit ctx: Context): String = ???
    def show(self: Tree)(implicit ctx: Context): String = ???
    def showWith(self: Tree, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }
  implicit def given_IsInstanceOf_PackageClause(implicit v: Context): IsInstanceOf[PackageClause] = ???
  object PackageClause {
    def apply(pid: Ref, stats: List[Tree])(implicit ctx: Context): PackageClause = ???
    def copy(original: Tree)(pid: Ref, stats: List[Tree])(implicit ctx: Context): PackageClause = ???
    def unapply(tree: PackageClause)(implicit ctx: Context): Some[(Ref, List[Tree])] = ???
  }
  object PackageClauseOps {
    def pid(self: PackageClause)(implicit ctx: Context): Ref = ???
    def stats(self: PackageClause)(implicit ctx: Context): List[Tree] = ???
  }
  implicit def given_IsInstanceOf_Import(implicit v: Context): IsInstanceOf[Import] = ???
  object Import {
    def apply(expr: Term, selectors: List[ImportSelector])(implicit ctx: Context): Import = ???
    def copy(original: Tree)(expr: Term, selectors: List[ImportSelector])(implicit ctx: Context): Import = ???
    def unapply(tree: Import)(implicit ctx: Context): Option[(Term, List[ImportSelector])] = ???
  }
  object ImportOps  {
    def expr(self: Import)(implicit ctx: Context): Term = ???
    def selectors(self: Import)(implicit ctx: Context): List[ImportSelector] = ???
  }
  implicit def given_IsInstanceOf_Statement(implicit v: Context): IsInstanceOf[Statement] = ???
  implicit def given_IsInstanceOf_Definition(implicit v: Context): IsInstanceOf[Definition] = ???
  object DefinitionOps {
    def name(self: Definition)(implicit ctx: Context): String = ???
  }
  implicit def given_IsInstanceOf_ClassDef(implicit v: Context): IsInstanceOf[ClassDef] = ???
  object ClassDef {
    def copy(original: Tree)(name: String, constr: DefDef, parents: List[Tree ], derived: List[TypeTree], selfOpt: Option[ValDef], body: List[Statement])(implicit ctx: Context): ClassDef = ???
    def unapply(cdef: ClassDef)(implicit ctx: Context): Option[(String, DefDef, List[Tree ], List[TypeTree], Option[ValDef], List[Statement])] = ???
  }
  object ClassDefOps {
    def constructor(self: ClassDef)(implicit ctx: Context): DefDef = ???
    def parents(self: ClassDef)(implicit ctx: Context): List[Tree ] = ???
    def derived(self: ClassDef)(implicit ctx: Context): List[TypeTree] = ???
    def self(self: ClassDef)(implicit ctx: Context): Option[ValDef] = ???
    def body(self: ClassDef)(implicit ctx: Context): List[Statement] = ???
  }
  implicit def given_IsInstanceOf_DefDef(implicit v: Context): IsInstanceOf[DefDef] = ???
  object DefDef {
    def apply(symbol: Symbol, rhsFn: List[Type] => List[List[Term]] => Option[Term])(implicit ctx: Context): DefDef = ???
    def copy(original: Tree)(name: String, typeParams: List[TypeDef], paramss: List[List[ValDef]], tpt: TypeTree, rhs: Option[Term])(implicit ctx: Context): DefDef = ???
    def unapply(ddef: DefDef)(implicit ctx: Context): Option[(String, List[TypeDef], List[List[ValDef]], TypeTree, Option[Term])] = ???
  }
  object DefDefOps {
    def typeParams(self: DefDef)(implicit ctx: Context): List[TypeDef] = ???
    def paramss(self: DefDef)(implicit ctx: Context): List[List[ValDef]] = ???
    def returnTpt(self: DefDef)(implicit ctx: Context): TypeTree = ???
    def rhs(self: DefDef)(implicit ctx: Context): Option[Term] = ???
  }
  implicit def given_IsInstanceOf_ValDef(implicit v: Context): IsInstanceOf[ValDef] = ???
  object ValDef {
    def apply(symbol: Symbol, rhs: Option[Term])(implicit ctx: Context): ValDef = ???
    def copy(original: Tree)(name: String, tpt: TypeTree, rhs: Option[Term])(implicit ctx: Context): ValDef = ???
    def unapply(vdef: ValDef)(implicit ctx: Context): Option[(String, TypeTree, Option[Term])] = ???
  }
  object ValDefOps {
    def tpt(self: ValDef)(implicit ctx: Context): TypeTree = ???
    def rhs(self: ValDef)(implicit ctx: Context): Option[Term] = ???
  }
  implicit def given_IsInstanceOf_TypeDef(implicit v: Context): IsInstanceOf[TypeDef] = ???
  object TypeDef {
    def apply(symbol: Symbol)(implicit ctx: Context): TypeDef = ???
    def copy(original: Tree)(name: String, rhs: Tree )(implicit ctx: Context): TypeDef = ???
    def unapply(tdef: TypeDef)(implicit ctx: Context): Option[(String, Tree  )] = ???
  }
  object TypeDefOps {
    def rhs(self: TypeDef)(implicit ctx: Context): Tree  = ???
  }
  implicit def given_IsInstanceOf_PackageDef(implicit v: Context): IsInstanceOf[PackageDef] = ???
  object PackageDefOps {
    def owner(self: PackageDef)(implicit ctx: Context): PackageDef = ???
    def members(self: PackageDef)(implicit ctx: Context): List[Statement] = ???
  }
  object PackageDef {
    def unapply(tree: PackageDef)(implicit ctx: Context): Option[(String, PackageDef)] = ???
  }
  object TermOps {
    def seal(self: Term)(implicit ctx: Context): scala.quoted.Expr[Any] = ???
    def tpe(self: Term)(implicit ctx: Context): Type = ???
    def underlyingArgument(self: Term)(implicit ctx: Context): Term = ???
    def underlying(self: Term)(implicit ctx: Context): Term = ???
    def etaExpand(self: Term)(implicit ctx: Context): Term = ???
    def appliedTo(self: Term, arg: Term)(implicit ctx: Context): Term = ???
    def appliedTo(self: Term, arg: Term, args: Term*)(implicit ctx: Context): Term = ???
    def appliedToArgs(self: Term, args: List[Term])(implicit ctx: Context): Apply = ???
    def appliedToArgss(self: Term, argss: List[List[Term]])(implicit ctx: Context): Term = ???
    def appliedToNone(self: Term)(implicit ctx: Context): Apply = ???
    def appliedToType(self: Term, targ: Type)(implicit ctx: Context): Term = ???
    def appliedToTypes(self: Term, targs: List[Type])(implicit ctx: Context): Term = ???
    def appliedToTypeTrees(self: Term)(targs: List[TypeTree])(implicit ctx: Context): Term = ???
    def select(self: Term)(sym: Symbol)(implicit ctx: Context): Select = ???
  }
  implicit def given_IsInstanceOf_Term(implicit v: Context): IsInstanceOf[Term] = ???
  implicit def given_IsInstanceOf_Ref(implicit v: Context): IsInstanceOf[Ref] = ???
  object Ref {
    def apply(sym: Symbol)(implicit ctx: Context): Ref = ???
  }
  implicit def given_IsInstanceOf_Ident(implicit v: Context): IsInstanceOf[Ident] = ???
  object IdentOps {
    def name(self: Ident)(implicit ctx: Context): String = ???
  }
  object Ident {
    def apply(tmref: TermRef)(implicit ctx: Context): Term = ???
    def copy(original: Tree)(name: String)(implicit ctx: Context): Ident = ???
    def unapply(tree: Ident)(implicit ctx: Context): Option[String] = ???
  }
  implicit def given_IsInstanceOf_Select(implicit v: Context): IsInstanceOf[Select] = ???
  object Select {
    def apply(qualifier: Term, symbol: Symbol)(implicit ctx: Context): Select = ???
    def unique(qualifier: Term, name: String)(implicit ctx: Context): Select = ???
    def overloaded(qualifier: Term, name: String, targs: List[Type], args: List[Term])(implicit ctx: Context): Apply = ???
    def copy(original: Tree)(qualifier: Term, name: String)(implicit ctx: Context): Select = ???
    def unapply(x: Select)(implicit ctx: Context): Option[(Term, String)] = ???
  }
  object SelectOps {
    def qualifier(self: Select)(implicit ctx: Context): Term = ???
    def name(self: Select)(implicit ctx: Context): String = ???
    def signature(self: Select)(implicit ctx: Context): Option[Signature] = ???
  }
  implicit def given_IsInstanceOf_Literal(implicit v: Context): IsInstanceOf[Literal] = ???
  object Literal {
    def apply(constant: Constant)(implicit ctx: Context): Literal = ???
    def copy(original: Tree)(constant: Constant)(implicit ctx: Context): Literal = ???
    def unapply(x: Literal)(implicit ctx: Context): Option[Constant] = ???
  }
  object LiteralOps {
    def constant(self: Literal)(implicit ctx: Context): Constant = ???
  }
  implicit def given_IsInstanceOf_This(implicit v: Context): IsInstanceOf[This] = ???
  object This {
    def apply(cls: Symbol)(implicit ctx: Context): This = ???
    def copy(original: Tree)(qual: Option[Id])(implicit ctx: Context): This = ???
    def unapply(x: This)(implicit ctx: Context): Option[Option[Id]] = ???
  }
  object ThisOps {
    def id(self: This)(implicit ctx: Context): Option[Id] = ???
  }
  implicit def given_IsInstanceOf_New(implicit v: Context): IsInstanceOf[New] = ???
  object New {
    def apply(tpt: TypeTree)(implicit ctx: Context): New = ???
    def copy(original: Tree)(tpt: TypeTree)(implicit ctx: Context): New = ???
    def unapply(x: New)(implicit ctx: Context): Option[TypeTree] = ???
  }
  object NewOps {
    def tpt(self: New)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_IsInstanceOf_NamedArg(implicit v: Context): IsInstanceOf[NamedArg] = ???
  object NamedArg {
    def apply(name: String, arg: Term)(implicit ctx: Context): NamedArg = ???
    def copy(original: Tree)(name: String, arg: Term)(implicit ctx: Context): NamedArg = ???
    def unapply(x: NamedArg)(implicit ctx: Context): Option[(String, Term)] = ???
  }
  object NamedArgOps {
    def name(self: NamedArg)(implicit ctx: Context): String = ???
    def value(self: NamedArg)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_Apply(implicit v: Context): IsInstanceOf[Apply] = ???
  object Apply {
    def apply(fun: Term, args: List[Term])(implicit ctx: Context): Apply = ???
    def copy(original: Tree)(fun: Term, args: List[Term])(implicit ctx: Context): Apply = ???
    def unapply(x: Apply)(implicit ctx: Context): Option[(Term, List[Term])] = ???
  }
  object ApplyOps {
    def fun(self: Apply)(implicit ctx: Context): Term = ???
    def args(self: Apply)(implicit ctx: Context): List[Term] = ???
  }
  implicit def given_IsInstanceOf_TypeApply(implicit v: Context): IsInstanceOf[TypeApply] = ???
  object TypeApply {
    def apply(fun: Term, args: List[TypeTree])(implicit ctx: Context): TypeApply = ???
    def copy(original: Tree)(fun: Term, args: List[TypeTree])(implicit ctx: Context): TypeApply = ???
    def unapply(x: TypeApply)(implicit ctx: Context): Option[(Term, List[TypeTree])] = ???
  }
  object TypeApplyOps {
    def fun(self: TypeApply)(implicit ctx: Context): Term = ???
    def args(self: TypeApply)(implicit ctx: Context): List[TypeTree] = ???
  }
  implicit def given_IsInstanceOf_Super(implicit v: Context): IsInstanceOf[Super] = ???
  object Super {
    def apply(qual: Term, mix: Option[Id])(implicit ctx: Context): Super = ???
    def copy(original: Tree)(qual: Term, mix: Option[Id])(implicit ctx: Context): Super = ???
    def unapply(x: Super)(implicit ctx: Context): Option[(Term, Option[Id])] = ???
  }
  object SuperOps {
    def qualifier(self: Super)(implicit ctx: Context): Term = ???
    def id(self: Super)(implicit ctx: Context): Option[Id] = ???
  }
  implicit def given_IsInstanceOf_Typed(implicit v: Context): IsInstanceOf[Typed] = ???
  object Typed {
    def apply(expr: Term, tpt: TypeTree)(implicit ctx: Context): Typed = ???
    def copy(original: Tree)(expr: Term, tpt: TypeTree)(implicit ctx: Context): Typed = ???
    def unapply(x: Typed)(implicit ctx: Context): Option[(Term, TypeTree)] = ???
  }
  object TypedOps {
    def expr(self: Typed)(implicit ctx: Context): Term = ???
    def tpt(self: Typed)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_IsInstanceOf_Assign(implicit v: Context): IsInstanceOf[Assign] = ???
  object Assign {
    def apply(lhs: Term, rhs: Term)(implicit ctx: Context): Assign = ???
    def copy(original: Tree)(lhs: Term, rhs: Term)(implicit ctx: Context): Assign = ???
    def unapply(x: Assign)(implicit ctx: Context): Option[(Term, Term)] = ???
  }
  object AssignOps {
    def lhs(self: Assign)(implicit ctx: Context): Term = ???
    def rhs(self: Assign)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_Block(implicit v: Context): IsInstanceOf[Block] = ???
  object Block {
    def apply(stats: List[Statement], expr: Term)(implicit ctx: Context): Block = ???
    def copy(original: Tree)(stats: List[Statement], expr: Term)(implicit ctx: Context): Block = ???
    def unapply(x: Block)(implicit ctx: Context): Option[(List[Statement], Term)] = ???
  }
  object BlockOps {
    def statements(self: Block)(implicit ctx: Context): List[Statement] = ???
    def expr(self: Block)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_Closure(implicit v: Context): IsInstanceOf[Closure] = ???
  object Closure {
    def apply(meth: Term, tpt: Option[Type])(implicit ctx: Context): Closure = ???
    def copy(original: Tree)(meth: Tree, tpt: Option[Type])(implicit ctx: Context): Closure = ???
    def unapply(x: Closure)(implicit ctx: Context): Option[(Term, Option[Type])] = ???
  }
  object ClosureOps {
    def meth(self: Closure)(implicit ctx: Context): Term = ???
    def tpeOpt(self: Closure)(implicit ctx: Context): Option[Type] = ???
  }
  object Lambda {
    def unapply(tree: Block)(implicit ctx: Context): Option[(List[ValDef], Term)] = ???
    def apply(tpe: MethodType, rhsFn: List[Tree] => Tree)(implicit ctx: Context): Block = ???
  }
  implicit def given_IsInstanceOf_If(implicit v: Context): IsInstanceOf[If] = ???
  object If {
    def apply(cond: Term, thenp: Term, elsep: Term)(implicit ctx: Context): If = ???
    def copy(original: Tree)(cond: Term, thenp: Term, elsep: Term)(implicit ctx: Context): If = ???
    def unapply(tree: If)(implicit ctx: Context): Option[(Term, Term, Term)] = ???
  }
  object IfOps {
    def cond(self: If)(implicit ctx: Context): Term = ???
    def thenp(self: If)(implicit ctx: Context): Term = ???
    def elsep(self: If)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_Match(implicit v: Context): IsInstanceOf[Match] = ???
  object Match {
    def apply(selector: Term, cases: List[CaseDef])(implicit ctx: Context): Match = ???
    def copy(original: Tree)(selector: Term, cases: List[CaseDef])(implicit ctx: Context): Match = ???
    def unapply(x: Match)(implicit ctx: Context): Option[(Term, List[CaseDef])] = ???
  }
  object MatchOps {
    def scrutinee(self: Match)(implicit ctx: Context): Term = ???
    def cases(self: Match)(implicit ctx: Context): List[CaseDef] = ???
  }
  implicit def given_IsInstanceOf_GivenMatch(implicit v: Context): IsInstanceOf[GivenMatch] = ???
  object GivenMatch {
    def apply(cases: List[CaseDef])(implicit ctx: Context): GivenMatch = ???
    def copy(original: Tree)(cases: List[CaseDef])(implicit ctx: Context): GivenMatch = ???
    def unapply(x: GivenMatch)(implicit ctx: Context): Option[List[CaseDef]] = ???
  }
  object GivenMatchOps {
    def cases(self: GivenMatch)(implicit ctx: Context): List[CaseDef] = ???
  }
  implicit def given_IsInstanceOf_Try(implicit v: Context): IsInstanceOf[Try] = ???
  object Try {
    def apply(expr: Term, cases: List[CaseDef], finalizer: Option[Term])(implicit ctx: Context): Try = ???
    def copy(original: Tree)(expr: Term, cases: List[CaseDef], finalizer: Option[Term])(implicit ctx: Context): Try = ???
    def unapply(x: Try)(implicit ctx: Context): Option[(Term, List[CaseDef], Option[Term])] = ???
  }
  object TryOps {
    def body(self: Try)(implicit ctx: Context): Term = ???
    def cases(self: Try)(implicit ctx: Context): List[CaseDef] = ???
    def finalizer(self: Try)(implicit ctx: Context): Option[Term] = ???
  }
  implicit def given_IsInstanceOf_Return(implicit v: Context): IsInstanceOf[Return] = ???
  object Return {
    def apply(expr: Term)(implicit ctx: Context): Return = ???
    def copy(original: Tree)(expr: Term)(implicit ctx: Context): Return = ???
    def unapply(x: Return)(implicit ctx: Context): Option[Term] = ???
  }
  object ReturnOps {
    def expr(self: Return)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_Repeated(implicit v: Context): IsInstanceOf[Repeated] = ???
  object Repeated {
    def apply(elems: List[Term], tpt: TypeTree)(implicit ctx: Context): Repeated = ???
    def copy(original: Tree)(elems: List[Term], tpt: TypeTree)(implicit ctx: Context): Repeated = ???
    def unapply(x: Repeated)(implicit ctx: Context): Option[(List[Term], TypeTree)] = ???
  }
  object RepeatedOps {
    def elems(self: Repeated)(implicit ctx: Context): List[Term] = ???
    def elemtpt(self: Repeated)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_IsInstanceOf_Inlined(implicit v: Context): IsInstanceOf[Inlined] = ???
  object Inlined {
    def apply(call: Option[Tree ], bindings: List[Definition], expansion: Term)(implicit ctx: Context): Inlined = ???
    def copy(original: Tree)(call: Option[Tree ], bindings: List[Definition], expansion: Term)(implicit ctx: Context): Inlined = ???
    def unapply(x: Inlined)(implicit ctx: Context): Option[(Option[Tree ], List[Definition], Term)] = ???
  }
  object InlinedOps {
    def call(self: Inlined)(implicit ctx: Context): Option[Tree ] = ???
    def bindings(self: Inlined)(implicit ctx: Context): List[Definition] = ???
    def body(self: Inlined)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_SelectOuter(implicit v: Context): IsInstanceOf[SelectOuter] = ???
  object SelectOuter {
    def apply(qualifier: Term, name: String, levels: Int)(implicit ctx: Context): SelectOuter = ???
    def copy(original: Tree)(qualifier: Term, name: String, levels: Int)(implicit ctx: Context): SelectOuter = ???
    def unapply(x: SelectOuter)(implicit ctx: Context): Option[(Term, Int, Type)] = ???
  }
  object SelectOuterOps {
    def qualifier(self: SelectOuter)(implicit ctx: Context): Term = ???
    def level(self: SelectOuter)(implicit ctx: Context): Int = ???
  }
  implicit def given_IsInstanceOf_While(implicit v: Context): IsInstanceOf[While] = ???
  object While {
    def apply(cond: Term, body: Term)(implicit ctx: Context): While = ???
    def copy(original: Tree)(cond: Term, body: Term)(implicit ctx: Context): While = ???
    def unapply(x: While)(implicit ctx: Context): Option[(Term, Term)] = ???
  }
  object WhileOps {
    def cond(self: While)(implicit ctx: Context): Term = ???
    def body(self: While)(implicit ctx: Context): Term = ???
  }
  object TypeTreeOps {
    def tpe(self: TypeTree)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_TypeTree(implicit v: Context): IsInstanceOf[TypeTree] = ???
  implicit def given_IsInstanceOf_Inferred(implicit v: Context): IsInstanceOf[Inferred] = ???
  object Inferred {
    def apply(tpe: Type)(implicit ctx: Context): Inferred = ???
    def unapply(x: Inferred)(implicit ctx: Context): Boolean = ???
  }
  implicit def given_IsInstanceOf_TypeIdent(implicit v: Context): IsInstanceOf[TypeIdent] = ???
  object TypeIdentOps {
    def name(self: TypeIdent)(implicit ctx: Context): String = ???
  }
  object TypeIdent {
    def apply(sym: Symbol)(implicit ctx: Context): TypeTree = ???
    def copy(original: Tree)(name: String)(implicit ctx: Context): TypeIdent = ???
    def unapply(x: TypeIdent)(implicit ctx: Context): Option[String] = ???
  }
  implicit def given_IsInstanceOf_TypeSelect(implicit v: Context): IsInstanceOf[TypeSelect] = ???
  object TypeSelect {
    def apply(qualifier: Term, name: String)(implicit ctx: Context): TypeSelect = ???
    def copy(original: Tree)(qualifier: Term, name: String)(implicit ctx: Context): TypeSelect = ???
    def unapply(x: TypeSelect)(implicit ctx: Context): Option[(Term, String)] = ???
  }
  object TypeSelectOps {
    def qualifier(self: TypeSelect)(implicit ctx: Context): Term = ???
    def name(self: TypeSelect)(implicit ctx: Context): String = ???
  }
  implicit def given_IsInstanceOf_Projection(implicit v: Context): IsInstanceOf[Projection] = ???
  object Projection {
    def copy(original: Tree)(qualifier: TypeTree, name: String)(implicit ctx: Context): Projection = ???
    def unapply(x: Projection)(implicit ctx: Context): Option[(TypeTree, String)] = ???
  }
  object ProjectionOps {
    def qualifier(self: Projection)(implicit ctx: Context): TypeTree = ???
    def name(self: Projection)(implicit ctx: Context): String = ???
  }
  implicit def given_IsInstanceOf_Singleton(implicit v: Context): IsInstanceOf[Singleton] = ???
  object Singleton {
    def apply(ref: Term)(implicit ctx: Context): Singleton = ???
    def copy(original: Tree)(ref: Term)(implicit ctx: Context): Singleton = ???
    def unapply(x: Singleton)(implicit ctx: Context): Option[Term] = ???
  }
  object SingletonOps {
    def ref(self: Singleton)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_Refined(implicit v: Context): IsInstanceOf[Refined] = ???
  object Refined {
    def copy(original: Tree)(tpt: TypeTree, refinements: List[Definition])(implicit ctx: Context): Refined = ???
    def unapply(x: Refined)(implicit ctx: Context): Option[(TypeTree, List[Definition])] = ???
  }
  object RefinedOps {
    def tpt(self: Refined)(implicit ctx: Context): TypeTree = ???
    def refinements(self: Refined)(implicit ctx: Context): List[Definition] = ???
  }
  implicit def given_IsInstanceOf_Applied(implicit v: Context): IsInstanceOf[Applied] = ???
  object Applied {
    def apply(tpt: TypeTree, args: List[Tree ])(implicit ctx: Context): Applied = ???
    def copy(original: Tree)(tpt: TypeTree, args: List[Tree ])(implicit ctx: Context): Applied = ???
    def unapply(x: Applied)(implicit ctx: Context): Option[(TypeTree, List[Tree ])] = ???
  }
  object AppliedOps {
    def tpt(self: Applied)(implicit ctx: Context): TypeTree = ???
    def args(self: Applied)(implicit ctx: Context): List[Tree ] = ???
  }
  implicit def given_IsInstanceOf_Annotated(implicit v: Context): IsInstanceOf[Annotated] = ???
  object Annotated {
    def apply(arg: TypeTree, annotation: Term)(implicit ctx: Context): Annotated = ???
    def copy(original: Tree)(arg: TypeTree, annotation: Term)(implicit ctx: Context): Annotated = ???
    def unapply(x: Annotated)(implicit ctx: Context): Option[(TypeTree, Term)] = ???
  }
  object AnnotatedOps {
    def arg(self: Annotated)(implicit ctx: Context): TypeTree = ???
    def annotation(self: Annotated)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_MatchTypeTree(implicit v: Context): IsInstanceOf[MatchTypeTree] = ???
  object MatchTypeTree {
    def apply(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef])(implicit ctx: Context): MatchTypeTree = ???
    def copy(original: Tree)(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef])(implicit ctx: Context): MatchTypeTree = ???
    def unapply(x: MatchTypeTree)(implicit ctx: Context): Option[(Option[TypeTree], TypeTree, List[TypeCaseDef])] = ???
  }
  object MatchTypeTreeOps {
    def bound(self: MatchTypeTree)(implicit ctx: Context): Option[TypeTree] = ???
    def selector(self: MatchTypeTree)(implicit ctx: Context): TypeTree = ???
    def cases(self: MatchTypeTree)(implicit ctx: Context): List[TypeCaseDef] = ???
  }
  implicit def given_IsInstanceOf_ByName(implicit v: Context): IsInstanceOf[ByName] = ???
  object ByName {
    def apply(result: TypeTree)(implicit ctx: Context): ByName = ???
    def copy(original: Tree)(result: TypeTree)(implicit ctx: Context): ByName = ???
    def unapply(x: ByName)(implicit ctx: Context): Option[TypeTree] = ???
  }
  object ByNameOps {
    def result(self: ByName)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_IsInstanceOf_LambdaTypeTree(implicit v: Context): IsInstanceOf[LambdaTypeTree] = ???
  object LambdaTypeTree {
    def apply(tparams: List[TypeDef], body: Tree )(implicit ctx: Context): LambdaTypeTree = ???
    def copy(original: Tree)(tparams: List[TypeDef], body: Tree )(implicit ctx: Context): LambdaTypeTree = ???
    def unapply(tree: LambdaTypeTree)(implicit ctx: Context): Option[(List[TypeDef], Tree )] = ???
  }
  object LambdaTypeTreeOps {
    def tparams(self: LambdaTypeTree)(implicit ctx: Context): List[TypeDef] = ???
    def body(self: LambdaTypeTree)(implicit ctx: Context): Tree  = ???
  }
  implicit def given_IsInstanceOf_TypeBind(implicit v: Context): IsInstanceOf[TypeBind] = ???
  object TypeBind {
    def copy(original: Tree)(name: String, tpt: Tree )(implicit ctx: Context): TypeBind = ???
    def unapply(x: TypeBind)(implicit ctx: Context): Option[(String, Tree )] = ???
  }
  object TypeBindOps {
    def name(self: TypeBind)(implicit ctx: Context): String = ???
    def body(self: TypeBind)(implicit ctx: Context): Tree  = ???
  }
  implicit def given_IsInstanceOf_TypeBlock(implicit v: Context): IsInstanceOf[TypeBlock] = ???
  object TypeBlock {
    def apply(aliases: List[TypeDef], tpt: TypeTree)(implicit ctx: Context): TypeBlock = ???
    def copy(original: Tree)(aliases: List[TypeDef], tpt: TypeTree)(implicit ctx: Context): TypeBlock = ???
    def unapply(x: TypeBlock)(implicit ctx: Context): Option[(List[TypeDef], TypeTree)] = ???
  }
  object TypeBlockOps {
    def aliases(self: TypeBlock)(implicit ctx: Context): List[TypeDef] = ???
    def tpt(self: TypeBlock)(implicit ctx: Context): TypeTree = ???
  }
  object TypeBoundsTreeOps {
    def tpe(self: TypeBoundsTree)(implicit ctx: Context): TypeBounds = ???
    def low(self: TypeBoundsTree)(implicit ctx: Context): TypeTree = ???
    def hi(self: TypeBoundsTree)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_IsInstanceOf_TypeBoundsTree(implicit v: Context): IsInstanceOf[TypeBoundsTree] = ???
  object TypeBoundsTree {
    def unapply(x: TypeBoundsTree)(implicit ctx: Context): Option[(TypeTree, TypeTree)] = ???
  }
  object WildcardTypeTreeOps {
    def tpe(self: WildcardTypeTree)(implicit ctx: Context): TypeOrBounds = ???
  }
  implicit def given_IsInstanceOf_WildcardTypeTree(implicit v: Context): IsInstanceOf[WildcardTypeTree] = ???
  object WildcardTypeTree {
    def unapply(x: WildcardTypeTree)(implicit ctx: Context): Boolean = ???
  }
  object CaseDefOps {
    def pattern(caseDef: CaseDef)(implicit ctx: Context): Tree = ???
    def guard(caseDef: CaseDef)(implicit ctx: Context): Option[Term] = ???
    def rhs(caseDef: CaseDef)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_CaseDef(implicit v: Context): IsInstanceOf[CaseDef] = ???
  object CaseDef {
    def apply(pattern: Tree, guard: Option[Term], rhs: Term)(implicit ctx: Context): CaseDef = ???
    def copy(original: Tree)(pattern: Tree, guard: Option[Term], rhs: Term)(implicit ctx: Context): CaseDef = ???
    def unapply(x: CaseDef)(implicit ctx: Context): Option[(Tree, Option[Term], Term)] = ???
  }
  object TypeCaseDefOps {
    def pattern(caseDef: TypeCaseDef)(implicit ctx: Context): TypeTree = ???
    def rhs(caseDef: TypeCaseDef)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_IsInstanceOf_TypeCaseDef(implicit v: Context): IsInstanceOf[TypeCaseDef] = ???
  object TypeCaseDef {
    def apply(pattern: TypeTree, rhs: TypeTree)(implicit ctx: Context): TypeCaseDef = ???
    def copy(original: Tree)(pattern: TypeTree, rhs: TypeTree)(implicit ctx: Context): TypeCaseDef = ???
    def unapply(tree: TypeCaseDef)(implicit ctx: Context): Option[(TypeTree, TypeTree)] = ???
  }
  implicit def given_IsInstanceOf_Bind(implicit v: Context): IsInstanceOf[Bind] = ???
  object Bind {
    def copy(original: Tree)(name: String, pattern: Tree)(implicit ctx: Context): Bind = ???
    def unapply(pattern: Bind)(implicit ctx: Context): Option[(String, Tree)] = ???
  }
  object BindOps {
    def name(bind: Bind)(implicit ctx: Context): String = ???
    def pattern(bind: Bind)(implicit ctx: Context): Tree = ???
  }
  implicit def given_IsInstanceOf_Unapply(implicit v: Context): IsInstanceOf[Unapply] = ???
  object Unapply {
    def copy(original: Tree)(fun: Term, implicits: List[Term], patterns: List[Tree])(implicit ctx: Context): Unapply = ???
    def unapply(x: Unapply)(implicit ctx: Context): Option[(Term, List[Term], List[Tree])] = ???
  }
  object UnapplyOps {
    def fun(unapply: Unapply)(implicit ctx: Context): Term = ???
    def implicits(unapply: Unapply)(implicit ctx: Context): List[Term] = ???
    def patterns(unapply: Unapply)(implicit ctx: Context): List[Tree] = ???
  }
  implicit def given_IsInstanceOf_Alternatives(implicit v: Context): IsInstanceOf[Alternatives] = ???
  object Alternatives {
    def apply(patterns: List[Tree])(implicit ctx: Context): Alternatives = ???
    def copy(original: Tree)(patterns: List[Tree])(implicit ctx: Context): Alternatives = ???
    def unapply(x: Alternatives)(implicit ctx: Context): Option[List[Tree]] = ???
  }
  object AlternativesOps {
    def patterns(alternatives: Alternatives)(implicit ctx: Context): List[Tree] = ???
  }

  // Import selectors

  object simpleSelectorOps {
    def selection(self: SimpleSelector)(implicit ctx: Context): Id = ???
  }
  implicit def given_IsInstanceOf_SimpleSelector(implicit v: Context): IsInstanceOf[SimpleSelector] = ???
  object SimpleSelector {
    def unapply(x: SimpleSelector)(implicit ctx: Context): Option[Id] = ???
  }
  object renameSelectorOps {
    def from(self: RenameSelector)(implicit ctx: Context): Id = ???
    def to(self: RenameSelector)(implicit ctx: Context): Id = ???
  }
  implicit def given_IsInstanceOf_RenameSelector(implicit v: Context): IsInstanceOf[RenameSelector] = ???
  object RenameSelector {
    def unapply(x: RenameSelector)(implicit ctx: Context): Option[(Id, Id)] = ???
  }
  object omitSelectorOps {
    def omitted(self: OmitSelector)(implicit ctx: Context): Id = ???
  }
  implicit def given_IsInstanceOf_OmitSelector(implicit v: Context): IsInstanceOf[OmitSelector] = ???
  object OmitSelector {
    def unapply(x: OmitSelector)(implicit ctx: Context): Option[Id] = ???
  }

  // Types

  object TypeOps {
    def seal(self: Type)(implicit ctx: Context): scala.quoted.Type[_] = ???
    def =:=(self: Type)(that: Type)(implicit ctx: Context): Boolean = ???
    def <:<(self: Type)(that: Type)(implicit ctx: Context): Boolean = ???
    def widen(self: Type)(implicit ctx: Context): Type = ???
    def widenTermRefExpr(self: Type)(implicit ctx: Context): Type = ???
    def dealias(self: Type)(implicit ctx: Context): Type = ???
    def simplified(self: Type)(implicit ctx: Context): Type = ???
    def classSymbol(self: Type)(implicit ctx: Context): Option[Symbol] = ???
    def typeSymbol(self: Type)(implicit ctx: Context): Symbol = ???
    def termSymbol(self: Type)(implicit ctx: Context): Symbol = ???
    def isSingleton(self: Type)(implicit ctx: Context): Boolean = ???
    def memberType(self: Type)(member: Symbol)(implicit ctx: Context): Type = ???
    def derivesFrom(self: Type)(cls: Symbol)(implicit ctx: Context): Boolean = ???
    def isFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def isContextFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def isErasedFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def isDependentFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
  }
  implicit def given_IsInstanceOf_Type(implicit v: Context): IsInstanceOf[Type] = ???
  object Type {
    def apply(clazz: Class[_])(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_ConstantType(implicit v: Context): IsInstanceOf[ConstantType] = ???
  object ConstantType {
    def apply(x : Constant)(implicit ctx: Context): ConstantType = ???
    def unapply(x: ConstantType)(implicit ctx: Context): Option[Constant] = ???
  }
  object ConstantTypeOps {
    def constant(self: ConstantType)(implicit ctx: Context): Constant = ???
  }
  implicit def given_IsInstanceOf_TermRef(implicit v: Context): IsInstanceOf[TermRef] = ???
  object TermRef {
    def apply(qual: TypeOrBounds, name: String)(implicit ctx: Context): TermRef = ???
    def unapply(x: TermRef)(implicit ctx: Context): Option[(TypeOrBounds , String)] = ???
  }
  object TermRefOps {
    def qualifier(self: TermRef)(implicit ctx: Context): TypeOrBounds  = ???
    def name(self: TermRef)(implicit ctx: Context): String = ???
  }
  implicit def given_IsInstanceOf_TypeRef(implicit v: Context): IsInstanceOf[TypeRef] = ???
  object TypeRef {
    def unapply(x: TypeRef)(implicit ctx: Context): Option[(TypeOrBounds , String)] = ???
  }
  object TypeRefOps {
    def qualifier(self: TypeRef)(implicit ctx: Context): TypeOrBounds  = ???
    def name(self: TypeRef)(implicit ctx: Context): String = ???
    def isOpaqueAlias(self: TypeRef)(implicit  ctx: Context): Boolean = ???
    def translucentSuperType(self: TypeRef)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_SuperType(implicit v: Context): IsInstanceOf[SuperType] = ???
  object SuperType {
    def apply(thistpe: Type, supertpe: Type)(implicit ctx: Context): SuperType = ???
    def unapply(x: SuperType)(implicit ctx: Context): Option[(Type, Type)] = ???
  }
  object SuperTypeOps {
    def thistpe(self: SuperType)(implicit ctx: Context): Type = ???
    def supertpe(self: SuperType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_Refinement(implicit v: Context): IsInstanceOf[Refinement] = ???
  object Refinement {
    def apply(parent: Type, name: String, info: TypeOrBounds )(implicit ctx: Context): Refinement = ???
    def unapply(x: Refinement)(implicit ctx: Context): Option[(Type, String, TypeOrBounds )] = ???
  }
  object RefinementOps {
    def parent(self: Refinement)(implicit ctx: Context): Type = ???
    def name(self: Refinement)(implicit ctx: Context): String = ???
    def info(self: Refinement)(implicit ctx: Context): TypeOrBounds = ???
  }
  implicit def given_IsInstanceOf_AppliedType(implicit v: Context): IsInstanceOf[AppliedType] = ???
  object AppliedType {
    def apply(tycon: Type, args: List[TypeOrBounds])(implicit ctx: Context): AppliedType = ???
    def unapply(x: AppliedType)(implicit ctx: Context): Option[(Type, List[TypeOrBounds ])] = ???
  }
  object AppliedTypeOps {
    def tycon(self: AppliedType)(implicit ctx: Context): Type = ???
    def args(self: AppliedType)(implicit ctx: Context): List[TypeOrBounds ] = ???
  }
  implicit def given_IsInstanceOf_AnnotatedType(implicit v: Context): IsInstanceOf[AnnotatedType] = ???
  object AnnotatedType {
    def apply(underlying: Type, annot: Term)(implicit ctx: Context): AnnotatedType = ???
    def unapply(x: AnnotatedType)(implicit ctx: Context): Option[(Type, Term)] = ???
  }
  object AnnotatedTypeOps {
    def underlying(self: AnnotatedType)(implicit ctx: Context): Type = ???
    def annot(self: AnnotatedType)(implicit ctx: Context): Term = ???
    def unapply(x: AnnotatedType)(implicit ctx: Context): Option[(Type, Term)] = ???
  }
  implicit def given_IsInstanceOf_AndType(implicit v: Context): IsInstanceOf[AndType] = ???
  object AndType {
    def apply(lhs: Type, rhs: Type)(implicit ctx: Context): AndType = ???
    def unapply(x: AndType)(implicit ctx: Context): Option[(Type, Type)] = ???
  }
  object AndTypeOps {
    def left(self: AndType)(implicit ctx: Context): Type = ???
    def right(self: AndType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_OrType(implicit v: Context): IsInstanceOf[OrType] = ???
  object OrType {
    def apply(lhs: Type, rhs: Type)(implicit ctx: Context): OrType = ???
    def unapply(x: OrType)(implicit ctx: Context): Option[(Type, Type)] = ???
  }
  object OrTypeOps {
    def left(self: OrType)(implicit ctx: Context): Type = ???
    def right(self: OrType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_MatchType(implicit v: Context): IsInstanceOf[MatchType] = ???
  object MatchType {
    def apply(bound: Type, scrutinee: Type, cases: List[Type])(implicit ctx: Context): MatchType = ???
    def unapply(x: MatchType)(implicit ctx: Context): Option[(Type, Type, List[Type])] = ???
  }
  object MatchTypeOps {
    def bound(self: MatchType)(implicit ctx: Context): Type = ???
    def scrutinee(self: MatchType)(implicit ctx: Context): Type = ???
    def cases(self: MatchType)(implicit ctx: Context): List[Type] = ???
  }
  def MatchCaseType(implicit ctx: Context): Type = ???
  implicit def given_IsInstanceOf_ByNameType(implicit v: Context): IsInstanceOf[ByNameType] = ???
  object ByNameType {
    def apply(underlying: Type)(implicit ctx: Context): Type = ???
    def unapply(x: ByNameType)(implicit ctx: Context): Option[Type] = ???
  }
  object ByNameTypeOps {
    def underlying(self: ByNameType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_ParamRef(implicit v: Context): IsInstanceOf[ParamRef] = ???
  object ParamRef {
    def unapply(x: ParamRef)(implicit ctx: Context): Option[(LambdaType[TypeOrBounds], Int)] = ???
  }
  object ParamRefOps {
    def binder(self: ParamRef)(implicit ctx: Context): LambdaType[TypeOrBounds] = ???
    def paramNum(self: ParamRef)(implicit ctx: Context): Int = ???
  }
  implicit def given_IsInstanceOf_ThisType(implicit v: Context): IsInstanceOf[ThisType] = ???
  object ThisType {
    def unapply(x: ThisType)(implicit ctx: Context): Option[Type] = ???
  }
  object ThisTypeOps {
    def tref(self: ThisType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_RecursiveThis(implicit v: Context): IsInstanceOf[RecursiveThis] = ???
  object RecursiveThis {
    def unapply(x: RecursiveThis)(implicit ctx: Context): Option[RecursiveType] = ???
  }
  object RecursiveThisOps {
    def binder(self: RecursiveThis)(implicit ctx: Context): RecursiveType = ???
  }
  implicit def given_IsInstanceOf_RecursiveType(implicit v: Context): IsInstanceOf[RecursiveType] = ???
  object RecursiveType {
    def apply(parentExp: RecursiveType => Type)(implicit ctx: Context): RecursiveType = ???
    def unapply(x: RecursiveType)(implicit ctx: Context): Option[Type] = ???
  }
  object RecursiveTypeOps {
    def underlying(self: RecursiveType)(implicit ctx: Context): Type = ???
    def recThis(self: RecursiveType)(implicit ctx: Context): RecursiveThis = ???
  }
  implicit def given_IsInstanceOf_MethodType(implicit v: Context): IsInstanceOf[MethodType] = ???
  object MethodType {
    def apply(paramNames: List[String])(paramInfosExp: MethodType => List[Type], resultTypeExp: MethodType => Type): MethodType = ???
    def unapply(x: MethodType)(implicit ctx: Context): Option[(List[String], List[Type], Type)] = ???
  }
  object MethodTypeOps {
    def isImplicit(self: MethodType): Boolean = ???
    def isErased(self: MethodType): Boolean = ???
    def param(self: MethodType)(idx: Int)(implicit ctx: Context): Type = ???
    def paramNames(self: MethodType)(implicit ctx: Context): List[String] = ???
    def paramTypes(self: MethodType)(implicit ctx: Context): List[Type] = ???
    def resType(self: MethodType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_PolyType(implicit v: Context): IsInstanceOf[PolyType] = ???
  object PolyType {
    def apply(paramNames: List[String])(paramBoundsExp: PolyType => List[TypeBounds], resultTypeExp: PolyType => Type)(implicit ctx: Context): PolyType = ???
    def unapply(x: PolyType)(implicit ctx: Context): Option[(List[String], List[TypeBounds], Type)] = ???
  }
  object PolyTypeOps {
    def param(self: PolyType)(idx: Int)(implicit ctx: Context): Type = ???
    def paramNames(self: PolyType)(implicit ctx: Context): List[String] = ???
    def paramBounds(self: PolyType)(implicit ctx: Context): List[TypeBounds] = ???
    def resType(self: PolyType)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_TypeLambda(implicit v: Context): IsInstanceOf[TypeLambda] = ???
  object TypeLambda {
    def apply(paramNames: List[String], boundsFn: TypeLambda => List[TypeBounds], bodyFn: TypeLambda => Type): TypeLambda = ???
    def unapply(x: TypeLambda)(implicit ctx: Context): Option[(List[String], List[TypeBounds], Type)] = ???
  }
  object TypeLambdaOps {
    def paramNames(self: TypeLambda)(implicit ctx: Context): List[String] = ???
    def paramBounds(self: TypeLambda)(implicit ctx: Context): List[TypeBounds] = ???
    def param(self: TypeLambda)(idx: Int)(implicit ctx: Context) : Type = ???
    def resType(self: TypeLambda)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_TypeBounds(implicit v: Context): IsInstanceOf[TypeBounds] = ???
  object TypeBounds {
    def apply(low: Type, hi: Type)(implicit ctx: Context): TypeBounds = ???
    def unapply(x: TypeBounds)(implicit ctx: Context): Option[(Type, Type)] = ???
  }
  object TypeBoundsOps {
    def low(self: TypeBounds)(implicit ctx: Context): Type = ???
    def hi(self: TypeBounds)(implicit ctx: Context): Type = ???
  }
  implicit def given_IsInstanceOf_NoPrefix(implicit v: Context): IsInstanceOf[NoPrefix] = ???
  object NoPrefix {
    def unapply(x: NoPrefix)(implicit ctx: Context): Boolean = ???
  }

  // Constants

  object ConstantOps {
    def value(const: Constant): Any = ???
    def showExtractors(const: Constant)(implicit ctx: Context): String = ???
    def show(const: Constant)(implicit ctx: Context): String = ???
    def showWith(const: Constant, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }
  object Constant {
    def apply(x: Any): Constant = ???
    def unapply(constant: Constant): Option[Any] = ???
    object ClassTag {
      def apply[T](implicit x: Type): Constant = ???
      def unapply(constant: Constant): Option[Type] = ???
    }
  }

  // IDs

  object IdOps {
    def pos(id: Id)(implicit ctx: Context): Position = ???
    def name(id: Id)(implicit ctx: Context): String = ???
  }
  object Id {
    def unapply(id: Id)(implicit ctx: Context): Option[String] = ???
  }

  // Implicits

  def searchImplicit(tpe: Type)(implicit ctx: Context): ImplicitSearchResult = ???
  implicit def given_IsInstanceOf_ImplicitSearchSuccess(implicit v: Context): IsInstanceOf[ImplicitSearchSuccess] = ???
  object successOps {
    def tree(self: ImplicitSearchSuccess)(implicit ctx: Context): Term = ???
  }
  implicit def given_IsInstanceOf_ImplicitSearchFailure(implicit v: Context): IsInstanceOf[ImplicitSearchFailure] = ???
  object failureOps {
    def explanation(self: ImplicitSearchFailure)(implicit ctx: Context): String = ???
  }
  implicit def given_IsInstanceOf_DivergingImplicit(implicit v: Context): IsInstanceOf[DivergingImplicit] = ???
  implicit def given_IsInstanceOf_NoMatchingImplicits(implicit v: Context): IsInstanceOf[NoMatchingImplicits] = ???
  implicit def given_IsInstanceOf_AmbiguousImplicits(implicit v: Context): IsInstanceOf[AmbiguousImplicits] = ???

  // Symbol

  object Symbol {
    def classSymbol(fullName: String)(implicit ctx: Context): Symbol = ???
    def newMethod(parent: Symbol, name: String, tpe: Type)(implicit ctx: Context): Symbol = ???
    def newMethod(parent: Symbol, name: String, tpe: Type, flags: Flags, privateWithin: Symbol)(implicit ctx: Context): Symbol = ???
    def newVal(parent: Symbol, name: String, tpe: Type, flags: Flags, privateWithin: Symbol)(implicit ctx: Context): Symbol = ???
    def noSymbol(implicit ctx: Context): Symbol = ???
  }
  object SymbolOps {
    def owner(self: Symbol)(implicit ctx: Context): Symbol = ???
    def maybeOwner(self: Symbol)(implicit ctx: Context): Symbol = ???
    def flags(self: Symbol)(implicit ctx: Context): Flags = ???
    def privateWithin(self: Symbol)(implicit ctx: Context): Option[Type] = ???
    def protectedWithin(self: Symbol)(implicit ctx: Context): Option[Type] = ???
    def name(self: Symbol)(implicit ctx: Context): String = ???
    def fullName(self: Symbol)(implicit ctx: Context): String = ???
    def pos(self: Symbol)(implicit ctx: Context): Position = ???
    def localContext(self: Symbol)(implicit ctx: Context): Context = ???
    def comment(self: Symbol)(implicit ctx: Context): Option[Comment] = ???
    def tree(self: Symbol)(implicit ctx: Context): Tree = ???
    def annots(self: Symbol)(implicit ctx: Context): List[Term] = ???
    def isDefinedInCurrentRun(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isLocalDummy(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isRefinementClass(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isAliasType(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isAnonymousClass(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isAnonymousFunction(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isAbstractType(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isClassConstructor(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isType(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isTerm(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isPackageDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isClassDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isTypeDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isValDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isDefDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isBind(self: Symbol)(implicit ctx: Context): Boolean = ???
    def isNoSymbol(self: Symbol)(implicit ctx: Context): Boolean = ???
    def exists(self: Symbol)(implicit ctx: Context): Boolean = ???
    def fields(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def field(self: Symbol)(name: String)(implicit ctx: Context): Symbol = ???
    def classMethod(self: Symbol)(name: String)(implicit ctx: Context): List[Symbol] = ???
    def classMethods(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def method(self: Symbol)(name: String)(implicit ctx: Context): List[Symbol] = ???
    def methods(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def caseFields(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def isTypeParam(self: Symbol)(implicit ctx: Context): Boolean = ???
    def signature(self: Symbol)(implicit ctx: Context): Signature = ???
    def moduleClass(self: Symbol)(implicit ctx: Context): Symbol = ???
    def companionClass(self: Symbol)(implicit ctx: Context): Symbol = ???
    def companionModule(self: Symbol)(implicit ctx: Context): Symbol = ???
    def showExtractors(symbol: Symbol)(implicit ctx: Context): String = ???
    def show(symbol: Symbol)(implicit ctx: Context): String = ???
    def showWith(symbol: Symbol, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
    def children(symbol: Symbol)(implicit ctx: Context): List[Symbol] = ???
  }

  // Signature

  object Signature {
    def unapply(sig: Signature)(implicit ctx: Context): Option[(List[Any], String)] = ???
  }
  object signatureOps {
    def paramSigs(sig: Signature): List[Any] = ???
    def resultSig(sig: Signature): String = ???
  }

  // Standard definitions

  object defn extends StandardSymbols with StandardTypes
  trait StandardSymbols {
    def RootPackage: Symbol = ???
    def RootClass: Symbol = ???
    def EmptyPackageClass: Symbol = ???
    def ScalaPackage: Symbol = ???
    def ScalaPackageClass: Symbol = ???
    def AnyClass: Symbol = ???
    def AnyValClass: Symbol = ???
    def ObjectClass: Symbol = ???
    def AnyRefClass: Symbol = ???
    def NullClass: Symbol = ???
    def NothingClass: Symbol = ???
    def UnitClass: Symbol = ???
    def ByteClass: Symbol = ???
    def ShortClass: Symbol = ???
    def CharClass: Symbol = ???
    def IntClass: Symbol = ???
    def LongClass: Symbol = ???
    def FloatClass: Symbol = ???
    def DoubleClass: Symbol = ???
    def BooleanClass: Symbol = ???
    def StringClass: Symbol = ???
    def ClassClass: Symbol = ???
    def ArrayClass: Symbol = ???
    def PredefModule: Symbol = ???
    def Predef_classOf: Symbol = ???
    def JavaLangPackage: Symbol = ???
    def ArrayModule: Symbol = ???
    def Array_apply: Symbol = ???
    def Array_clone: Symbol = ???
    def Array_length: Symbol = ???
    def Array_update: Symbol = ???
    def RepeatedParamClass: Symbol = ???
    def OptionClass: Symbol = ???
    def NoneModule: Symbol = ???
    def SomeModule: Symbol = ???
    def ProductClass: Symbol = ???
    def FunctionClass(arity: Int, isImplicit: Boolean = false, isErased: Boolean = false): Symbol = ???
    def TupleClass(arity: Int): Symbol = ???
    def isTupleClass(sym: Symbol): Boolean = ???
    def ScalaPrimitiveValueClasses: List[Symbol] = ???
    def ScalaNumericValueClasses: List[Symbol] = ???
  }
  trait StandardTypes {
    def UnitType: Type = ???
    def ByteType: Type = ???
    def ShortType: Type = ???
    def CharType: Type = ???
    def IntType: Type = ???
    def LongType: Type = ???
    def FloatType: Type = ???
    def DoubleType: Type = ???
    def BooleanType: Type = ???
    def AnyType: Type = ???
    def AnyValType: Type = ???
    def AnyRefType: Type = ???
    def ObjectType: Type = ???
    def NothingType: Type = ???
    def NullType: Type = ???
    def StringType: Type = ???
  }

  // Flags

  object FlagsOps {
    def is(self: Flags)(that: Flags): Boolean = ???
    def |(self: Flags)(that: Flags): Flags = ???
    def &(self: Flags)(that: Flags): Flags = ???
    def showExtractors(flags: Flags)(implicit ctx: Context): String = ???
    def show(flags: Flags)(implicit ctx: Context): String = ???
    def showWith(flags: Flags, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }
  object Flags {
    def EmptyFlags: Flags = ???
    def Private: Flags = ???
    def Protected: Flags = ???
    def Abstract: Flags = ???
    def Final: Flags = ???
    def Sealed: Flags = ???
    def Case: Flags = ???
    def Implicit: Flags = ???
    def Given: Flags = ???
    def Erased: Flags = ???
    def Lazy: Flags = ???
    def Override: Flags = ???
    def Inline: Flags = ???
    def Macro: Flags = ???
    def Static: Flags = ???
    def JavaDefined: Flags = ???
    def Object: Flags = ???
    def Trait: Flags = ???
    def Local: Flags = ???
    def Synthetic: Flags = ???
    def Artifact: Flags = ???
    def Mutable: Flags = ???
    def FieldAccessor: Flags = ???
    def CaseAcessor: Flags = ???
    def Covariant: Flags = ???
    def Contravariant: Flags = ???
    def Scala2X: Flags = ???
    def DefaultParameterized: Flags = ???
    def StableRealizable: Flags = ???
    def Param: Flags = ???
    def ParamAccessor: Flags = ???
    def Enum: Flags = ???
    def ModuleClass: Flags = ???
    def PrivateLocal: Flags = ???
    def Package: Flags = ???
  }

  // Positions

  def rootPosition: Position = ???

  object positionOps {
    def start(pos: Position): Int = ???
    def end(pos: Position): Int = ???
    def exists(pos: Position): Boolean = ???
    def sourceFile(pos: Position): SourceFile = ???
    def startLine(pos: Position): Int = ???
    def endLine(pos: Position): Int = ???
    def startColumn(pos: Position): Int = ???
    def endColumn(pos: Position): Int = ???
    def sourceCode(pos: Position): String = ???
  }
  object sourceFileOps {
    def jpath(sourceFile: SourceFile): java.nio.file.Path = ???
    def content(sourceFile: SourceFile): String = ???
  }

  // Reporting

  def error(msg: => String, pos: Position)(implicit ctx: Context): Unit = ???
  def error(msg: => String, source: SourceFile, start: Int, end: Int)(implicit ctx: Context): Unit = ???
  def warning(msg: => String, pos: Position)(implicit ctx: Context): Unit = ???
  def warning(msg: => String, source: SourceFile, start: Int, end: Int)(implicit ctx: Context): Unit = ???

  // Printers
   implicit class TreeShowDeco(tree: Tree) {
    def showExtractors(implicit ctx: Context): String = ???
    def show(implicit ctx: Context): String = ???
    def show(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }
  implicit class TypeOrBoundsShowDeco(tpe: TypeOrBounds) {
    def showExtractors(implicit ctx: Context): String = ???
    def show(implicit ctx: Context): String = ???
    def show(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }
  implicit class ConstantShowDeco(const: Constant) {
    def showExtractors(implicit ctx: Context): String = ???
    def show(implicit ctx: Context): String = show(SyntaxHighlight.plain)(ctx)
    def show(syntaxHighlight: SyntaxHighlight)(implicit  ctx: Context): String = ???
  }
  implicit class SymbolShowDeco(symbol: Symbol) {
    def showExtractors(implicit ctx: Context): String = ???
    def show(implicit ctx: Context): String = show(SyntaxHighlight.plain)(ctx)
    def show(syntaxHighlight: SyntaxHighlight)(implicit  ctx: Context): String = ???
  }
  implicit class FlagsShowDeco(flags: Flags) {
    def showExtractors(implicit ctx: Context): String = ???
    def show(implicit ctx: Context): String = show(SyntaxHighlight.plain)(ctx)
    def show(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }

  // Comments

  object CommentOps {
    def raw(self: Comment): String = ???
    def expanded(self: Comment): Option[String] = ???
    def usecases(self: Comment): List[(String, Option[DefDef])] = ???
  }

  // Utils

  trait TreeAccumulator[X] extends reflect.TreeAccumulator[X] {
    val reflect: self.type = self
  }
  trait TreeTraverser extends reflect.TreeTraverser {
    val reflect: self.type = self
  }
  trait TreeMap extends reflect.TreeMap {
    val reflect: self.type = self
  }
  def let(rhs: Term)(body: Ident => Term): Term = ???
  def lets(terms: List[Term])(body: List[Term] => Term): Term = ???

}
