package scala.quoted

import scala.reflect.TypeTest

// TODO In Scala 2.13.4 it should be possible to access Scala 3 ABI directly (because scalac can now read TASTy).

// Reproduces the ABI of https://github.com/lampepfl/dotty/blob/M2/library/src/scala/quoted/Quotes.scala
// Requires the corresponding "compat" class to reproduce the API.
trait Quotes {
  val reflect: Reflection

  trait Reflection { reflectSelf =>

    // Trees

    type Tree <: AnyRef
    val Tree: TreeModule
    trait TreeModule {
      def of(expr: Expr[Any]): Tree
    }
    protected[quoted] val TreeMethodsImpl: TreeMethods
    trait TreeMethods {
      def pos(self: Tree): Position
      def symbol(self: Tree): Symbol
      def showExtractors(self: Tree): String
      def show(self: Tree): String
      def showAnsiColored(self: Tree): String
      def isExpr(self: Tree): Boolean
      def asExpr(self: Tree): Expr[Any]
      def asExprOf[T](self: Tree)(implicit t: Type[T]): Expr[T]
    }

    type PackageClause <: Tree
    implicit def given_TypeTest_Tree_PackageClause: TypeTest[Tree, PackageClause]
    val PackageClause: PackageClauseModule
    trait PackageClauseModule {
      def apply(pid: Ref, stats: List[Tree]): PackageClause
      def copy(original: Tree)(pid: Ref, stats: List[Tree]): PackageClause
      def unapply(tree: PackageClause): Some[(Ref, List[Tree])]
    }
    protected[quoted] val PackageClauseMethodsImpl: PackageClauseMethods
    trait PackageClauseMethods {
      def pid(self: PackageClause): Ref
      def stats(self: PackageClause): List[Tree]
    }

    type Import <: Statement
    implicit def given_TypeTest_Tree_Import: TypeTest[Tree, Import]
    val Import: ImportModule
    trait ImportModule {
      def apply(expr: Term, selectors: List[ImportSelector]): Import
      def copy(original: Tree)(expr: Term, selectors: List[ImportSelector]): Import
      def unapply(tree: Import): Option[(Term, List[ImportSelector])]
    }
    protected[quoted] val ImportMethodsImpl: ImportMethods
    trait ImportMethods {
      def expr(self: Import): Term
      def selectors(self: Import): List[ImportSelector]
    }

    type Statement <: Tree
    implicit def given_TypeTest_Tree_Statement: TypeTest[Tree, Statement]

    type Definition <: Statement
    implicit def given_TypeTest_Tree_Definition: TypeTest[Tree, Definition]
    val Definition: DefinitionModule
    trait DefinitionModule
    protected[quoted] val DefinitionMethodsImpl: DefinitionMethods
    trait DefinitionMethods {
      def name(self: Definition): String
    }

    type ClassDef <: Definition
    implicit def given_TypeTest_Tree_ClassDef: TypeTest[Tree, ClassDef]
    val ClassDef: ClassDefModule
    trait ClassDefModule {
      def copy(original: Tree)(name: String, constr: DefDef, parents: List[Tree], derived: List[TypeTree], selfOpt: Option[ValDef], body: List[Statement]): ClassDef
      def unapply(cdef: ClassDef): Option[(String, DefDef, List[Tree], List[TypeTree], Option[ValDef], List[Statement])]
    }
    protected[quoted] val ClassDefMethodsImpl: ClassDefMethods
    trait ClassDefMethods {
      def constructor(self: ClassDef): DefDef
      def parents(self: ClassDef): List[Tree]
      def derived(self: ClassDef): List[TypeTree]
      def self(self: ClassDef): Option[ValDef]
      def body(self: ClassDef): List[Statement]
    }

    type DefDef <: Definition
    implicit def given_TypeTest_Tree_DefDef: TypeTest[Tree, DefDef]
    val DefDef: DefDefModule
    trait DefDefModule {
      def apply(symbol: Symbol, rhsFn: List[TypeRepr] => List[List[Term]] => Option[Term]): DefDef
      def copy(original: Tree)(name: String, typeParams: List[TypeDef], paramss: List[List[ValDef]], tpt: TypeTree, rhs: Option[Term]): DefDef
      def unapply(ddef: DefDef): Option[(String, List[TypeDef], List[List[ValDef]], TypeTree, Option[Term])]
    }
    protected[quoted] val DefDefMethodsImpl: DefDefMethods
    trait DefDefMethods {
      def typeParams(self: DefDef): List[TypeDef]
      def paramss(self: DefDef): List[List[ValDef]]
      def returnTpt(self: DefDef): TypeTree
      def rhs(self: DefDef): Option[Term]
    }

    type ValDef <: Definition
    implicit def given_TypeTest_Tree_ValDef: TypeTest[Tree, ValDef]
    val ValDef: ValDefModule
    trait ValDefModule {
      def apply(symbol: Symbol, rhs: Option[Term]): ValDef
      def copy(original: Tree)(name: String, tpt: TypeTree, rhs: Option[Term]): ValDef
      def unapply(vdef: ValDef): Option[(String, TypeTree, Option[Term])]
      def let(owner: Symbol, name: String, rhs: Term)(body: Ident => Term): Term
      def let(owner: Symbol, rhs: Term)(body: Ident => Term): Term
      def let(owner: Symbol, terms: List[Term])(body: List[Ident] => Term): Term
    }
    protected[quoted] val ValDefMethodsImpl: ValDefMethods
    trait ValDefMethods {
      def tpt(self: ValDef): TypeTree
      def rhs(self: ValDef): Option[Term]
    }

    type TypeDef <: Definition
    implicit def given_TypeTest_Tree_TypeDef: TypeTest[Tree, TypeDef]
    val TypeDef: TypeDefModule
    trait TypeDefModule {
      def apply(symbol: Symbol): TypeDef
      def copy(original: Tree)(name: String, rhs: Tree ): TypeDef
      def unapply(tdef: TypeDef): Option[(String, Tree  )]
    }
    protected[quoted] val TypeDefMethodsImpl: TypeDefMethods
    trait TypeDefMethods {
      def rhs(self: TypeDef): Tree
    }

    type Term <: Statement
    implicit def given_TypeTest_Tree_Term: TypeTest[Tree, Term]
    val Term: TermModule
    trait TermModule {
      def of(expr: Expr[Any]): Term
      def betaReduce(term: Term): Option[Term]
    }
    protected[quoted] val TermMethodsImpl: TermMethods
    trait TermMethods {
      def seal(self: Term): scala.quoted.Expr[Any]
      def sealOpt(self: Term): Option[scala.quoted.Expr[Any]]
      def tpe(self: Term): TypeRepr
      def underlyingArgument(self: Term): Term
      def underlying(self: Term): Term
      def etaExpand(self: Term, owner: Symbol): Term
      def appliedTo(self: Term, arg: Term): Term
      def appliedTo(self: Term, arg: Term, args: Term*): Term
      def appliedToArgs(self: Term, args: List[Term]): Apply
      def appliedToArgss(self: Term, argss: List[List[Term]]): Term
      def appliedToNone(self: Term): Apply
      def appliedToType(self: Term, targ: TypeRepr): Term
      def appliedToTypes(self: Term, targs: List[TypeRepr]): Term
      def appliedToTypeTrees(self: Term)(targs: List[TypeTree]): Term
      def select(self: Term)(sym: Symbol): Select
    }

    type Ref <: Term
    implicit def given_TypeTest_Tree_Ref: TypeTest[Tree, Ref]
    val Ref: RefModule
    trait RefModule {
      def apply(sym: Symbol): Ref
      def term(tp: TermRef): Ref
    }

    type Ident <: Ref
    implicit def given_TypeTest_Tree_Ident: TypeTest[Tree, Ident]
    val Ident: IdentModule
    trait IdentModule {
      def apply(tmref: TermRef): Term
      def copy(original: Tree)(name: String): Ident
      def unapply(tree: Ident): Option[String]
    }
    protected[quoted] val IdentMethodsImpl: IdentMethods
    trait IdentMethods {
      def name(self: Ident): String
    }

    type Select <: Ref
    implicit def given_TypeTest_Tree_Select: TypeTest[Tree, Select]
    val Select: SelectModule
    trait SelectModule {
      def apply(qualifier: Term, symbol: Symbol): Select
      def unique(qualifier: Term, name: String): Select
      def overloaded(qualifier: Term, name: String, targs: List[TypeRepr], args: List[Term]): Apply
      def copy(original: Tree)(qualifier: Term, name: String): Select
      def unapply(x: Select): Option[(Term, String)]
    }
    protected[quoted] val SelectMethodsImpl: SelectMethods
    trait SelectMethods {
      def qualifier(self: Select): Term
      def name(self: Select): String
      def signature(self: Select): Option[Signature]
    }

    type Literal <: Term
    implicit def given_TypeTest_Tree_Literal: TypeTest[Tree, Literal]
    val Literal: LiteralModule
    trait LiteralModule {
      def apply(constant: Constant): Literal
      def copy(original: Tree)(constant: Constant): Literal
      def unapply(x: Literal): Option[Constant]
    }
    protected[quoted] val LiteralMethodsImpl: LiteralMethods
    trait LiteralMethods {
      def constant(self: Literal): Constant
    }

    type This <: Term
    implicit def given_TypeTest_Tree_This: TypeTest[Tree, This]
    val This: ThisModule
    trait ThisModule {
      def apply(cls: Symbol): This
      def copy(original: Tree)(qual: Option[String]): This
      def unapply(x: This): Option[Option[String]]
    }
    protected[quoted] val ThisMethodsImpl: ThisMethods
    trait ThisMethods {
      def id(self: This): Option[String]
    }

    type New <: Term
    implicit def given_TypeTest_Tree_New: TypeTest[Tree, New]
    val New: NewModule
    trait NewModule {
      def apply(tpt: TypeTree): New
      def copy(original: Tree)(tpt: TypeTree): New
      def unapply(x: New): Option[TypeTree]
    }
    protected[quoted] val NewMethodsImpl: NewMethods
    trait NewMethods {
      def tpt(self: New): TypeTree
    }

    type NamedArg <: Term
    implicit def given_TypeTest_Tree_NamedArg: TypeTest[Tree, NamedArg]
    val NamedArg: NamedArgModule
    trait NamedArgModule {
      def apply(name: String, arg: Term): NamedArg
      def copy(original: Tree)(name: String, arg: Term): NamedArg
      def unapply(x: NamedArg): Option[(String, Term)]
    }
    protected[quoted] val NamedArgMethodsImpl: NamedArgMethods
    trait NamedArgMethods {
      def name(self: NamedArg): String
      def value(self: NamedArg): Term
    }

    type Apply <: Term
    implicit def given_TypeTest_Tree_Apply: TypeTest[Tree, Apply]
    val Apply: ApplyModule
    trait ApplyModule {
      def apply(fun: Term, args: List[Term]): Apply
      def copy(original: Tree)(fun: Term, args: List[Term]): Apply
      def unapply(x: Apply): Option[(Term, List[Term])]
    }
    protected[quoted] val ApplyMethodsImpl: ApplyMethods
    trait ApplyMethods {
      def fun(self: Apply): Term
      def args(self: Apply): List[Term]
    }

    type TypeApply <: Term
    implicit def given_TypeTest_Tree_TypeApply: TypeTest[Tree, TypeApply]
    val TypeApply: TypeApplyModule
    trait TypeApplyModule {
      def apply(fun: Term, args: List[TypeTree]): TypeApply
      def copy(original: Tree)(fun: Term, args: List[TypeTree]): TypeApply
      def unapply(x: TypeApply): Option[(Term, List[TypeTree])]
    }
    protected[quoted] val TypeApplyMethodsImpl: TypeApplyMethods
    trait TypeApplyMethods {
      def fun(self: TypeApply): Term
      def args(self: TypeApply): List[TypeTree]
    }

    type Super <: Term
    implicit def given_TypeTest_Tree_Super: TypeTest[Tree, Super]
    val Super: SuperModule
    trait SuperModule {
      def apply(qual: Term, mix: Option[String]): Super
      def copy(original: Tree)(qual: Term, mix: Option[String]): Super
      def unapply(x: Super): Option[(Term, Option[String])]
    }
    protected[quoted] val SuperMethodsImpl: SuperMethods
    trait SuperMethods {
      def qualifier(self: Super): Term
      def id(self: Super): Option[String]
      def idPos(self: Super): Position
    }

    type Typed <: Term
    implicit def given_TypeTest_Tree_Typed: TypeTest[Tree, Typed]
    val Typed: TypedModule
    trait TypedModule {
      def apply(expr: Term, tpt: TypeTree): Typed
      def copy(original: Tree)(expr: Term, tpt: TypeTree): Typed
      def unapply(x: Typed): Option[(Term, TypeTree)]
    }
    protected[quoted] val TypedMethodsImpl: TypedMethods
    trait TypedMethods {
      def expr(self: Typed): Term
      def tpt(self: Typed): TypeTree
    }

    type Assign <: Term
    implicit def given_TypeTest_Tree_Assign: TypeTest[Tree, Assign]
    val Assign: AssignModule
    trait AssignModule {
      def apply(lhs: Term, rhs: Term): Assign
      def copy(original: Tree)(lhs: Term, rhs: Term): Assign
      def unapply(x: Assign): Option[(Term, Term)]
    }
    protected[quoted] val AssignMethodsImpl: AssignMethods
    trait AssignMethods {
      def lhs(self: Assign): Term
      def rhs(self: Assign): Term
    }

    type Block <: Term
    implicit def given_TypeTest_Tree_Block: TypeTest[Tree, Block]
    val Block: BlockModule
    trait BlockModule {
      def apply(stats: List[Statement], expr: Term): Block
      def copy(original: Tree)(stats: List[Statement], expr: Term): Block
      def unapply(x: Block): Option[(List[Statement], Term)]
    }
    protected[quoted] val BlockMethodsImpl: BlockMethods
    trait BlockMethods {
      def statements(self: Block): List[Statement]
      def expr(self: Block): Term
    }

    type Closure <: Term
    implicit def given_TypeTest_Tree_Closure: TypeTest[Tree, Closure]
    val Closure: ClosureModule
    trait ClosureModule {
      def apply(meth: Term, tpt: Option[TypeRepr]): Closure
      def copy(original: Tree)(meth: Tree, tpt: Option[TypeRepr]): Closure
      def unapply(x: Closure): Option[(Term, Option[TypeRepr])]
    }
    protected[quoted] val ClosureMethodsImpl: ClosureMethods
    trait ClosureMethods {
      def meth(self: Closure): Term
      def tpeOpt(self: Closure): Option[TypeRepr]
    }

    val Lambda: LambdaModule
    trait LambdaModule {
      def unapply(tree: Block): Option[(List[ValDef], Term)]
      def apply(owner: Symbol, tpe: MethodType, rhsFn: (Symbol, List[Tree]) => Tree): Block
    }

    type If <: Term
    implicit def given_TypeTest_Tree_If: TypeTest[Tree, If]
    val If: IfModule
    trait IfModule {
      def apply(cond: Term, thenp: Term, elsep: Term): If
      def copy(original: Tree)(cond: Term, thenp: Term, elsep: Term): If
      def unapply(tree: If): Option[(Term, Term, Term)]
    }
    protected[quoted] val IfMethodsImpl: IfMethods
    trait IfMethods {
      def cond(self: If): Term
      def thenp(self: If): Term
      def elsep(self: If): Term
    }

    type Match <: Term
    implicit def given_TypeTest_Tree_Match: TypeTest[Tree, Match]
    val Match: MatchModule
    trait MatchModule {
      def apply(selector: Term, cases: List[CaseDef]): Match
      def copy(original: Tree)(selector: Term, cases: List[CaseDef]): Match
      def unapply(x: Match): Option[(Term, List[CaseDef])]
    }
    protected[quoted] val MatchMethodsImpl: MatchMethods
    trait MatchMethods {
      def scrutinee(self: Match): Term
      def cases(self: Match): List[CaseDef]
    }

    type SummonFrom <: Term
    implicit def given_TypeTest_Tree_SummonFrom: TypeTest[Tree, SummonFrom]
    val SummonFrom: SummonFromModule
    trait SummonFromModule {
      def apply(cases: List[CaseDef]): SummonFrom
      def copy(original: Tree)(cases: List[CaseDef]): SummonFrom
      def unapply(x: SummonFrom): Option[List[CaseDef]]
    }
    protected[quoted] val SummonFromMethodsImpl: SummonFromMethods
    trait SummonFromMethods {
      def cases(self: SummonFrom): List[CaseDef]
    }

    type Try <: Term
    implicit def given_TypeTest_Tree_Try: TypeTest[Tree, Try]
    val Try: TryModule
    trait TryModule {
      def apply(expr: Term, cases: List[CaseDef], finalizer: Option[Term]): Try
      def copy(original: Tree)(expr: Term, cases: List[CaseDef], finalizer: Option[Term]): Try
      def unapply(x: Try): Option[(Term, List[CaseDef], Option[Term])]
    }
    protected[quoted] val TryMethodsImpl: TryMethods
    trait TryMethods {
      def body(self: Try): Term
      def cases(self: Try): List[CaseDef]
      def finalizer(self: Try): Option[Term]
    }

    type Return <: Term
    implicit def given_TypeTest_Tree_Return: TypeTest[Tree, Return]
    val Return: ReturnModule
    trait ReturnModule {
      def apply(expr: Term, from: Symbol): Return
      def copy(original: Tree)(expr: Term, from: Symbol): Return
      def unapply(x: Return): Option[(Term, Symbol)]
    }
    protected[quoted] val ReturnMethodsImpl: ReturnMethods
    trait ReturnMethods {
      def expr(self: Return): Term
      def from(self: Return): Symbol
    }

    type Repeated <: Term
    implicit def given_TypeTest_Tree_Repeated: TypeTest[Tree, Repeated]
    val Repeated: RepeatedModule
    trait RepeatedModule {
      def apply(elems: List[Term], tpt: TypeTree): Repeated
      def copy(original: Tree)(elems: List[Term], tpt: TypeTree): Repeated
      def unapply(x: Repeated): Option[(List[Term], TypeTree)]
    }
    protected[quoted] val RepeatedMethodsImpl: RepeatedMethods
    trait RepeatedMethods {
      def elems(self: Repeated): List[Term]
      def elemtpt(self: Repeated): TypeTree
    }

    type Inlined <: Term
    implicit def given_TypeTest_Tree_Inlined: TypeTest[Tree, Inlined]
    val Inlined: InlinedModule
    trait InlinedModule {
      def apply(call: Option[Tree ], bindings: List[Definition], expansion: Term): Inlined
      def copy(original: Tree)(call: Option[Tree ], bindings: List[Definition], expansion: Term): Inlined
      def unapply(x: Inlined): Option[(Option[Tree ], List[Definition], Term)]
    }
    protected[quoted] val InlinedMethodsImpl: InlinedMethods
    trait InlinedMethods {
      def call(self: Inlined): Option[Tree ]
      def bindings(self: Inlined): List[Definition]
      def body(self: Inlined): Term
    }

    type SelectOuter <: Term
    implicit def given_TypeTest_Tree_SelectOuter: TypeTest[Tree, SelectOuter]
    trait SelectOuterModule {
      def apply(qualifier: Term, name: String, levels: Int): SelectOuter
      def copy(original: Tree)(qualifier: Term, name: String, levels: Int): SelectOuter
      def unapply(x: SelectOuter): Option[(Term, Int, TypeRepr)]
    }
    protected[quoted] val SelectOuterMethodsImpl: SelectOuterMethods
    trait SelectOuterMethods {
      def qualifier(self: SelectOuter): Term
      def name(self: SelectOuter): String
      def level(self: SelectOuter): Int
    }

    type While <: Term
    implicit def given_TypeTest_Tree_While: TypeTest[Tree, While]
    val While: WhileModule
    trait WhileModule {
      def apply(cond: Term, body: Term): While
      def copy(original: Tree)(cond: Term, body: Term): While
      def unapply(x: While): Option[(Term, Term)]
    }
    protected[quoted] val WhileMethodsImpl: WhileMethods
    trait WhileMethods {
      def cond(self: While): Term
      def body(self: While): Term
    }

    type TypeTree <: Tree
    implicit def given_TypeTest_Tree_TypeTree: TypeTest[Tree, TypeTree]
    val TypeTree: TypeTreeModule
    trait TypeTreeModule {
      def of[T](implicit t: Type[T]): TypeTree
    }
    protected[quoted] val TypeTreeMethodsImpl: TypeTreeMethods
    trait TypeTreeMethods {
      def tpe(self: TypeTree): TypeRepr
    }

    type Inferred <: TypeTree
    implicit def given_TypeTest_Tree_Inferred: TypeTest[Tree, Inferred]
    val Inferred: InferredModule
    trait InferredModule {
      def apply(tpe: TypeRepr): Inferred
      def unapply(x: Inferred): Boolean
    }

    type TypeIdent <: TypeTree
    implicit def given_TypeTest_Tree_TypeIdent: TypeTest[Tree, TypeIdent]
    val TypeIdent: TypeIdentModule
    trait TypeIdentModule {
      def apply(sym: Symbol): TypeTree
      def copy(original: Tree)(name: String): TypeIdent
      def unapply(x: TypeIdent): Option[String]
    }
    protected[quoted] val TypeIdentMethodsImpl: TypeIdentMethods
    trait TypeIdentMethods {
      def name(self: TypeIdent): String
    }

    type TypeSelect <: TypeTree
    implicit def given_TypeTest_Tree_TypeSelect: TypeTest[Tree, TypeSelect]
    val TypeSelect: TypeSelectModule
    trait TypeSelectModule {
      def apply(qualifier: Term, name: String): TypeSelect
      def copy(original: Tree)(qualifier: Term, name: String): TypeSelect
      def unapply(x: TypeSelect): Option[(Term, String)]
    }
    protected[quoted] val TypeSelectMethodsImpl: TypeSelectMethods
    trait TypeSelectMethods {
      def qualifier(self: TypeSelect): Term
      def name(self: TypeSelect): String
    }

    type TypeProjection <: TypeTree
    implicit def given_TypeTest_Tree_TypeProjection: TypeTest[Tree, TypeProjection]
    val TypeProjection: TypeProjectionModule
    trait TypeProjectionModule {
      def copy(original: Tree)(qualifier: TypeTree, name: String): TypeProjection
      def unapply(x: TypeProjection): Option[(TypeTree, String)]
    }
    protected[quoted] val TypeProjectionMethodsImpl: TypeProjectionMethods
    trait TypeProjectionMethods {
      def qualifier(self: TypeProjection): TypeTree
      def name(self: TypeProjection): String
    }

    type Singleton <: TypeTree
    implicit def given_TypeTest_Tree_Singleton: TypeTest[Tree, Singleton]
    val Singleton: SingletonModule
    trait SingletonModule {
      def apply(ref: Term): Singleton
      def copy(original: Tree)(ref: Term): Singleton
      def unapply(x: Singleton): Option[Term]
    }
    protected[quoted] val SingletonMethodsImpl: SingletonMethods
    trait SingletonMethods {
      def ref(self: Singleton): Term
    }

    type Refined <: TypeTree
    implicit def given_TypeTest_Tree_Refined: TypeTest[Tree, Refined]
    val Refined: RefinedModule
    protected[quoted] val RefinedMethodsImpl: RefinedMethods
    trait RefinedModule {
      def copy(original: Tree)(tpt: TypeTree, refinements: List[Definition]): Refined
      def unapply(x: Refined): Option[(TypeTree, List[Definition])]
    }
    trait RefinedMethods {
      def tpt(self: Refined): TypeTree
      def refinements(self: Refined): List[Definition]
    }

    type Applied <: TypeTree
    implicit def given_TypeTest_Tree_Applied: TypeTest[Tree, Applied]
    val Applied: AppliedModule
    trait AppliedModule {
      def apply(tpt: TypeTree, args: List[Tree ]): Applied
      def copy(original: Tree)(tpt: TypeTree, args: List[Tree ]): Applied
      def unapply(x: Applied): Option[(TypeTree, List[Tree ])]
    }
    protected[quoted] val AppliedMethodsImpl: AppliedMethods
    trait AppliedMethods {
      def tpt(self: Applied): TypeTree
      def args(self: Applied): List[Tree ]
    }

    type Annotated <: TypeTree
    implicit def given_TypeTest_Tree_Annotated: TypeTest[Tree, Annotated]
    val Annotated: AnnotatedModule
    trait AnnotatedModule {
      def apply(arg: TypeTree, annotation: Term): Annotated
      def copy(original: Tree)(arg: TypeTree, annotation: Term): Annotated
      def unapply(x: Annotated): Option[(TypeTree, Term)]
    }
    protected[quoted] val AnnotatedMethodsImpl: AnnotatedMethods
    trait AnnotatedMethods {
      def arg(self: Annotated): TypeTree
      def annotation(self: Annotated): Term
    }

    type MatchTypeTree <: TypeTree
    implicit def given_TypeTest_Tree_MatchTypeTree: TypeTest[Tree, MatchTypeTree]
    val MatchTypeTree: MatchTypeTreeModule
    trait MatchTypeTreeModule {
      def apply(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef]): MatchTypeTree
      def copy(original: Tree)(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef]): MatchTypeTree
      def unapply(x: MatchTypeTree): Option[(Option[TypeTree], TypeTree, List[TypeCaseDef])]
    }
    protected[quoted] val MatchTypeTreeMethodsImpl: MatchTypeTreeMethods
    trait MatchTypeTreeMethods {
      def bound(self: MatchTypeTree): Option[TypeTree]
      def selector(self: MatchTypeTree): TypeTree
      def cases(self: MatchTypeTree): List[TypeCaseDef]
    }

    type ByName <: TypeTree
    implicit def given_TypeTest_Tree_ByName: TypeTest[Tree, ByName]
    val ByName: ByNameModule
    trait ByNameModule {
      def apply(result: TypeTree): ByName
      def copy(original: Tree)(result: TypeTree): ByName
      def unapply(x: ByName): Option[TypeTree]
    }
    protected[quoted] val ByNameMethodsImpl: ByNameMethods
    trait ByNameMethods {
      def result(self: ByName): TypeTree
    }

    type LambdaTypeTree <: TypeTree
    implicit def given_TypeTest_Tree_LambdaTypeTree: TypeTest[Tree, LambdaTypeTree]
    val LambdaTypeTree: LambdaTypeTreeModule
    trait LambdaTypeTreeModule {
      def apply(tparams: List[TypeDef], body: Tree ): LambdaTypeTree
      def copy(original: Tree)(tparams: List[TypeDef], body: Tree ): LambdaTypeTree
      def unapply(tree: LambdaTypeTree): Option[(List[TypeDef], Tree )]
    }
    protected[quoted] val LambdaTypeTreeMethodsImpl: LambdaTypeTreeMethods
    trait LambdaTypeTreeMethods {
      def tparams(self: LambdaTypeTree): List[TypeDef]
      def body(self: LambdaTypeTree): Tree
    }

    type TypeBind <: TypeTree
    implicit def given_TypeTest_Tree_TypeBind: TypeTest[Tree, TypeBind]
    val TypeBind: TypeBindModule
    trait TypeBindModule {
      def copy(original: Tree)(name: String, tpt: Tree ): TypeBind
      def unapply(x: TypeBind): Option[(String, Tree )]
    }
    protected[quoted] val TypeBindMethodsImpl: TypeBindMethods
    trait TypeBindMethods {
      def name(self: TypeBind): String
      def body(self: TypeBind): Tree
    }

    type TypeBlock <: TypeTree
    implicit def given_TypeTest_Tree_TypeBlock: TypeTest[Tree, TypeBlock]
    val TypeBlock: TypeBlockModule
    trait TypeBlockModule {
      def apply(aliases: List[TypeDef], tpt: TypeTree): TypeBlock
      def copy(original: Tree)(aliases: List[TypeDef], tpt: TypeTree): TypeBlock
      def unapply(x: TypeBlock): Option[(List[TypeDef], TypeTree)]
    }
    protected[quoted] val TypeBlockMethodsImpl: TypeBlockMethods
    trait TypeBlockMethods {
      def aliases(self: TypeBlock): List[TypeDef]
      def tpt(self: TypeBlock): TypeTree
    }

    type TypeBoundsTree <: Tree /*TypeTree | TypeBoundsTree*/
    implicit def given_TypeTest_Tree_TypeBoundsTree: TypeTest[Tree, TypeBoundsTree]
    val TypeBoundsTree: TypeBoundsTreeModule
    trait TypeBoundsTreeModule {
      def apply(low: TypeTree, hi: TypeTree): TypeBoundsTree
      def copy(original: Tree)(low: TypeTree, hi: TypeTree): TypeBoundsTree
      def unapply(x: TypeBoundsTree): Option[(TypeTree, TypeTree)]
    }
    protected[quoted] val TypeBoundsTreeMethodsImpl: TypeBoundsTreeMethods
    trait TypeBoundsTreeMethods {
      def tpe(self: TypeBoundsTree): TypeBounds
      def low(self: TypeBoundsTree): TypeTree
      def hi(self: TypeBoundsTree): TypeTree
    }

    type WildcardTypeTree  <: Tree
    implicit def given_TypeTest_Tree_WildcardTypeTree: TypeTest[Tree, WildcardTypeTree]
    val WildcardTypeTree: WildcardTypeTreeModule
    trait WildcardTypeTreeModule {
      def apply(tpe: TypeRepr): WildcardTypeTree
      def unapply(x: WildcardTypeTree): Boolean
    }
    protected[quoted] val WildcardTypeTreeMethodsImpl: WildcardTypeTreeMethods
    trait WildcardTypeTreeMethods {
      def tpe(self: WildcardTypeTree): TypeRepr
    }

    type CaseDef <: Tree
    implicit def given_TypeTest_Tree_CaseDef: TypeTest[Tree, CaseDef]
    val CaseDef: CaseDefModule
    trait CaseDefModule {
      def apply(pattern: Tree, guard: Option[Term], rhs: Term): CaseDef
      def copy(original: Tree)(pattern: Tree, guard: Option[Term], rhs: Term): CaseDef
      def unapply(x: CaseDef): Option[(Tree, Option[Term], Term)]
    }
    protected[quoted] val CaseDefMethodsImpl: CaseDefMethods
    trait CaseDefMethods {
      def pattern(caseDef: CaseDef): Tree
      def guard(caseDef: CaseDef): Option[Term]
      def rhs(caseDef: CaseDef): Term
    }

    type TypeCaseDef <: Tree
    implicit def given_TypeTest_Tree_TypeCaseDef: TypeTest[Tree, TypeCaseDef]
    val TypeCaseDef: TypeCaseDefModule
    trait TypeCaseDefModule {
      def apply(pattern: TypeTree, rhs: TypeTree): TypeCaseDef
      def copy(original: Tree)(pattern: TypeTree, rhs: TypeTree): TypeCaseDef
      def unapply(tree: TypeCaseDef): Option[(TypeTree, TypeTree)]
    }
    protected[quoted] val TypeCaseDefMethodsImpl: TypeCaseDefMethods
    trait TypeCaseDefMethods {
      def pattern(caseDef: TypeCaseDef): TypeTree
      def rhs(caseDef: TypeCaseDef): TypeTree
    }

    type Bind <: Tree
    implicit def given_TypeTest_Tree_Bind: TypeTest[Tree, Bind]
    val Bind: BindModule
    trait BindModule {
      def copy(original: Tree)(name: String, pattern: Tree): Bind
      def unapply(pattern: Bind): Option[(String, Tree)]
    }
    protected[quoted] val BindMethodsImpl: BindMethods
    trait BindMethods {
      def name(bind: Bind): String
      def pattern(bind: Bind): Tree
    }

    type Unapply <: Tree
    implicit def given_TypeTest_Tree_Unapply: TypeTest[Tree, Unapply]
    val Unapply: UnapplyModule
    trait UnapplyModule {
      def copy(original: Tree)(fun: Term, implicits: List[Term], patterns: List[Tree]): Unapply
      def unapply(x: Unapply): Option[(Term, List[Term], List[Tree])]
    }
    protected[quoted] val UnapplyMethodsImpl: UnapplyMethods
    trait UnapplyMethods {
      def fun(unapply: Unapply): Term
      def implicits(unapply: Unapply): List[Term]
      def patterns(unapply: Unapply): List[Tree]
    }

    type Alternatives <: Tree
    implicit def given_TypeTest_Tree_Alternatives: TypeTest[Tree, Alternatives]
    val Alternatives: AlternativesModule
    trait AlternativesModule {
      def apply(patterns: List[Tree]): Alternatives
      def copy(original: Tree)(patterns: List[Tree]): Alternatives
      def unapply(x: Alternatives): Option[List[Tree]]
    }
    protected[quoted] val AlternativesMethodsImpl: AlternativesMethods
    trait AlternativesMethods {
      def patterns(alternatives: Alternatives): List[Tree]
    }

    // Import selectors

    type ImportSelector <: AnyRef
    val ImportSelector: ImportSelectorModule
    trait ImportSelectorModule

    type SimpleSelector <: ImportSelector
    implicit def given_TypeTest_ImportSelector_SimpleSelector: TypeTest[ImportSelector, SimpleSelector]
    val SimpleSelector: SimpleSelectorModule
    trait SimpleSelectorModule {
      def unapply(x: SimpleSelector): Option[String]
    }
    protected[quoted] val SimpleSelectorMethodsImpl: SimpleSelectorMethods
    trait SimpleSelectorMethods {
      def name(self: SimpleSelector): String
      def namePos(self: SimpleSelector): Position
    }

    type RenameSelector <: ImportSelector
    implicit def given_TypeTest_ImportSelector_RenameSelector: TypeTest[ImportSelector, RenameSelector]
    val RenameSelector: RenameSelectorModule
    trait RenameSelectorModule {
      def unapply(x: RenameSelector): Option[(String, String)]
    }
    protected[quoted] val RenameSelectorMethodsImpl: RenameSelectorMethods
    trait RenameSelectorMethods {
      def fromName(self: RenameSelector): String
      def fromPos(self: RenameSelector): Position
      def toName(self: RenameSelector): String
      def toPos(self: RenameSelector): Position
    }

    type OmitSelector <: ImportSelector
    implicit def given_TypeTest_ImportSelector_OmitSelector: TypeTest[ImportSelector, OmitSelector]
    val OmitSelector: OmitSelectorModule
    trait OmitSelectorModule {
      def unapply(x: OmitSelector): Option[String]
    }
    protected[quoted] val OmitSelectorMethodsImpl: OmitSelectorMethods
    trait OmitSelectorMethods {
      def name(self: OmitSelector): String
      def namePos(self: OmitSelector): Position
    }

    // Types

  //  def typeOf[T](implicit qtype: scala.quoted.Type[T], ctx: Context): Type

    type TypeRepr
    val TypeRepr: TypeReprModule
    trait TypeReprModule {
      def of[T](implicit qtype: scala.quoted.Type[T]): TypeRepr
      def typeConstructorOf(clazz: Class[_]): TypeRepr
    }
    protected[quoted] val TypeReprMethodsImpl: TypeReprMethods
    trait TypeReprMethods {
      def showExtractors(self: TypeRepr): String
      def show(self: TypeRepr): String
      def showAnsiColored(self: TypeRepr): String
      def asType(self: TypeRepr): Type[_]
      def =:=(self: TypeRepr)(that: TypeRepr): Boolean
      def <:<(self: TypeRepr)(that: TypeRepr): Boolean
      def widen(self: TypeRepr): TypeRepr
      def widenTermRefExpr(self: TypeRepr): TypeRepr
      def dealias(self: TypeRepr): TypeRepr
      def simplified(self: TypeRepr): TypeRepr
      def classSymbol(self: TypeRepr): Option[Symbol]
      def typeSymbol(self: TypeRepr): Symbol
      def termSymbol(self: TypeRepr): Symbol
      def isSingleton(self: TypeRepr): Boolean
      def memberType(self: TypeRepr)(member: Symbol): TypeRepr
      def baseClasses(self: TypeRepr): List[Symbol]
      def baseType(self: TypeRepr)(member: Symbol): TypeRepr
      def derivesFrom(self: TypeRepr)(cls: Symbol): Boolean
      def isFunctionType(self: TypeRepr): Boolean
      def isContextFunctionType(self: TypeRepr): Boolean
      def isErasedFunctionType(self: TypeRepr): Boolean
      def isDependentFunctionType(self: TypeRepr): Boolean
      def select(self: TypeRepr)(sym: Symbol): TypeRepr
      def appliedTo(self: TypeRepr, targ: TypeRepr): TypeRepr
      def appliedTo(self: TypeRepr, targs: List[TypeRepr]): TypeRepr
    }

    type ConstantType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_ConstantType: TypeTest[TypeRepr, ConstantType]
    val ConstantType: ConstantTypeModule
    trait ConstantTypeModule {
      def apply(x : Constant): ConstantType
      def unapply(x: ConstantType): Option[Constant]
    }
    protected[quoted] val ConstantTypeMethodsImpl: ConstantTypeMethods
    trait ConstantTypeMethods {
      def constant(self: ConstantType): Constant
    }

    type TermRef <: TypeRepr
    implicit def given_TypeTest_TypeRepr_TermRef: TypeTest[TypeRepr, TermRef]
    val TermRef: TermRefModule
    trait TermRefModule {
      def apply(qual: TypeRepr, name: String): TermRef
      def unapply(x: TermRef): Option[(TypeRepr , String)]
    }
    protected[quoted] val TermRefMethodsImpl: TermRefMethods
    trait TermRefMethods {
      def qualifier(self: TermRef): TypeRepr
      def name(self: TermRef): String
    }

    type TypeRef <: TypeRepr
    implicit def given_TypeTest_TypeRepr_TypeRef: TypeTest[TypeRepr, TypeRef]
    val TypeRef: TypeRefModule
    trait TypeRefModule {
      def unapply(x: TypeRef): Option[(TypeRepr, String)]
    }
    protected[quoted] val TypeRefMethodsImpl: TypeRefMethods
    trait TypeRefMethods {
      def qualifier(self: TypeRef): TypeRepr
      def name(self: TypeRef): String
      def isOpaqueAlias(self: TypeRef): Boolean
      def translucentSuperType(self: TypeRef): TypeRepr
    }

    type SuperType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_SuperType: TypeTest[TypeRepr, SuperType]
    val SuperType: SuperTypeModule
    trait SuperTypeModule {
      def apply(thistpe: TypeRepr, supertpe: TypeRepr): SuperType
      def unapply(x: SuperType): Option[(TypeRepr, TypeRepr)]
    }
    protected[quoted] val SuperTypeMethodsImpl: SuperTypeMethods
    trait SuperTypeMethods {
      def thistpe(self: SuperType): TypeRepr
      def supertpe(self: SuperType): TypeRepr
    }

    type Refinement <: TypeRepr
    implicit def given_TypeTest_TypeRepr_Refinement: TypeTest[TypeRepr, Refinement]
    val Refinement: RefinementModule
    trait RefinementModule {
      def apply(parent: TypeRepr, name: String, info: TypeRepr): Refinement
      def unapply(x: Refinement): Option[(TypeRepr, String, TypeRepr )]
    }
    protected[quoted] val RefinementMethodsImpl: RefinementMethods
    trait RefinementMethods {
      def parent(self: Refinement): TypeRepr
      def name(self: Refinement): String
      def info(self: Refinement): TypeRepr
    }

    type AppliedType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_AppliedType: TypeTest[TypeRepr, AppliedType]
    val AppliedType: AppliedTypeModule
    trait AppliedTypeModule {
      def apply(tycon: TypeRepr, args: List[TypeRepr]): AppliedType
      def unapply(x: AppliedType): Option[(TypeRepr, List[TypeRepr ])]
    }
    protected[quoted] val AppliedTypeMethodsImpl: AppliedTypeMethods
    trait AppliedTypeMethods {
      def tycon(self: AppliedType): TypeRepr
      def args(self: AppliedType): List[TypeRepr]
    }

    type AnnotatedType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_AnnotatedType: TypeTest[TypeRepr, AnnotatedType]
    val AnnotatedType: AnnotatedTypeModule
    trait AnnotatedTypeModule {
      def apply(underlying: TypeRepr, annot: Term): AnnotatedType
      def unapply(x: AnnotatedType): Option[(TypeRepr, Term)]
    }
    protected[quoted] val AnnotatedTypeMethodsImpl: AnnotatedTypeMethods
    trait AnnotatedTypeMethods {
      def underlying(self: AnnotatedType): TypeRepr
      def annot(self: AnnotatedType): Term
    }

    type AndType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_AndType: TypeTest[TypeRepr, AndType]
    val AndType: AndTypeModule
    trait AndTypeModule {
      def apply(lhs: TypeRepr, rhs: TypeRepr): AndType
      def unapply(x: AndType): Option[(TypeRepr, TypeRepr)]
    }
    protected[quoted] val AndTypeMethodsImpl: AndTypeMethods
    trait AndTypeMethods {
      def left(self: AndType): TypeRepr
      def right(self: AndType): TypeRepr
    }

    type OrType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_OrType: TypeTest[TypeRepr, OrType]
    val OrType: OrTypeModule
    trait OrTypeModule {
      def apply(lhs: TypeRepr, rhs: TypeRepr): OrType
      def unapply(x: OrType): Option[(TypeRepr, TypeRepr)]
    }
    protected[quoted] val OrTypeMethodsImpl: OrTypeMethods
    trait OrTypeMethods {
      def left(self: OrType): TypeRepr
      def right(self: OrType): TypeRepr
    }

    type MatchType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_MatchType: TypeTest[TypeRepr, MatchType]
    val MatchType: MatchTypeModule
    trait MatchTypeModule {
      def apply(bound: TypeRepr, scrutinee: TypeRepr, cases: List[TypeRepr]): MatchType
      def unapply(x: MatchType): Option[(TypeRepr, TypeRepr, List[TypeRepr])]
    }
    protected[quoted] val MatchTypeMethodsImpl: MatchTypeMethods
    trait MatchTypeMethods {
      def bound(self: MatchType): TypeRepr
      def scrutinee(self: MatchType): TypeRepr
      def cases(self: MatchType): List[TypeRepr]
    }

  //  def MatchCaseType: Type

    type ByNameType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_ByNameType: TypeTest[TypeRepr, ByNameType]
    val ByNameType: ByNameTypeModule
    trait ByNameTypeModule {
      def apply(underlying: TypeRepr): TypeRepr
      def unapply(x: ByNameType): Option[TypeRepr]
    }
    protected[quoted] val ByNameTypeMethodsImpl: ByNameTypeMethods
    trait ByNameTypeMethods {
      def underlying(self: ByNameType): TypeRepr
    }

    type ParamRef <: TypeRepr
    implicit def given_TypeTest_TypeRepr_ParamRef: TypeTest[TypeRepr, ParamRef]
    val ParamRef: ParamRefModule
    trait ParamRefModule {
      def unapply(x: ParamRef): Option[(LambdaType, Int)]
    }
    protected[quoted] val ParamRefMethodsImpl: ParamRefMethods
    trait ParamRefMethods {
      def binder(self: ParamRef): LambdaType
      def paramNum(self: ParamRef): Int
    }

    type ThisType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_ThisType: TypeTest[TypeRepr, ThisType]
    val ThisType: ThisTypeModule
    trait ThisTypeModule {
      def unapply(x: ThisType): Option[TypeRepr]
    }
    protected[quoted] val ThisTypeMethodsImpl: ThisTypeMethods
    trait ThisTypeMethods {
      def tref(self: ThisType): TypeRepr
    }

    type RecursiveThis <: TypeRepr
    implicit def given_TypeTest_TypeRepr_RecursiveThis: TypeTest[TypeRepr, RecursiveThis]
    val RecursiveThis: RecursiveThisModule
    trait RecursiveThisModule {
      def unapply(x: RecursiveThis): Option[RecursiveType]
    }
    protected[quoted] val RecursiveThisMethodsImpl: RecursiveThisMethods
    trait RecursiveThisMethods {
      def binder(self: RecursiveThis): RecursiveType
    }

    type RecursiveType <: TypeRepr
    implicit def given_TypeTest_TypeRepr_RecursiveType: TypeTest[TypeRepr, RecursiveType]
    val RecursiveType: RecursiveTypeModule
    trait RecursiveTypeModule {
      def apply(parentExp: RecursiveType => TypeRepr): RecursiveType
      def unapply(x: RecursiveType): Option[TypeRepr]
    }
    protected[quoted] val RecursiveTypeMethodsImpl: RecursiveTypeMethods
    trait RecursiveTypeMethods {
      def underlying(self: RecursiveType): TypeRepr
      def recThis(self: RecursiveType): RecursiveThis
    }

    type LambdaType <: TypeRepr

    type MethodType <: LambdaType
    implicit def given_TypeTest_TypeRepr_MethodType: TypeTest[TypeRepr, MethodType]
    val MethodType: MethodTypeModule
    trait MethodTypeModule {
      def apply(paramNames: List[String])(paramInfosExp: MethodType => List[TypeRepr], resultTypeExp: MethodType => TypeRepr): MethodType
      def unapply(x: MethodType): Option[(List[String], List[TypeRepr], TypeRepr)]
    }
    protected[quoted] val MethodTypeMethodsImpl: MethodTypeMethods
    trait MethodTypeMethods {
      def isImplicit(self: MethodType): Boolean
      def isErased(self: MethodType): Boolean
      def param(self: MethodType)(idx: Int): TypeRepr
      def paramNames(self: MethodType): List[String]
      def paramTypes(self: MethodType): List[TypeRepr]
      def resType(self: MethodType): TypeRepr
    }

    type PolyType <: LambdaType
    implicit def given_TypeTest_TypeRepr_PolyType: TypeTest[TypeRepr, PolyType]
    val PolyType: PolyTypeModule
    trait PolyTypeModule {
      def apply(paramNames: List[String])(paramBoundsExp: PolyType => List[TypeBounds], resultTypeExp: PolyType => TypeRepr): PolyType
      def unapply(x: PolyType): Option[(List[String], List[TypeBounds], TypeRepr)]
    }
    protected[quoted] val PolyTypeMethodsImpl: PolyTypeMethods
    trait PolyTypeMethods {
      def param(self: PolyType)(idx: Int): TypeRepr
      def paramNames(self: PolyType): List[String]
      def paramBounds(self: PolyType): List[TypeBounds]
      def resType(self: PolyType): TypeRepr
    }

    type TypeLambda <: LambdaType
    implicit def given_TypeTest_TypeRepr_TypeLambda: TypeTest[TypeRepr, TypeLambda]
    val TypeLambda: TypeLambdaModule
    trait TypeLambdaModule {
      def apply(paramNames: List[String], boundsFn: TypeLambda => List[TypeBounds], bodyFn: TypeLambda => TypeRepr): TypeLambda
      def unapply(x: TypeLambda): Option[(List[String], List[TypeBounds], TypeRepr)]
    }
    protected[quoted] val TypeLambdaMethodsImpl: TypeLambdaMethods
    trait TypeLambdaMethods {
      def paramNames(self: TypeLambda): List[String]
      def paramBounds(self: TypeLambda): List[TypeBounds]
      def param(self: TypeLambda)(idx: Int) : TypeRepr
      def resType(self: TypeLambda): TypeRepr
    }

    type TypeBounds <: TypeRepr
    implicit def given_TypeTest_TypeRepr_TypeBounds: TypeTest[TypeRepr, TypeBounds]
    val TypeBounds: TypeBoundsModule
    trait TypeBoundsModule {
      def apply(low: TypeRepr, hi: TypeRepr): TypeBounds
      def unapply(x: TypeBounds): Option[(TypeRepr, TypeRepr)]
    }
    protected[quoted] val TypeBoundsMethodsImpl: TypeBoundsMethods
    trait TypeBoundsMethods {
      def low(self: TypeBounds): TypeRepr
      def hi(self: TypeBounds): TypeRepr
    }

    type NoPrefix <: TypeRepr
    implicit def given_TypeTest_TypeRepr_NoPrefix: TypeTest[TypeRepr, NoPrefix]
    val NoPrefix: NoPrefixModule
    trait NoPrefixModule {
      def unapply(x: NoPrefix): Boolean
    }

    // Constants

    type Constant <: AnyRef
    val Constant: ConstantModule
    trait ConstantModule {
      val Boolean: ConstantBooleanModule
      trait ConstantBooleanModule {
        def apply(x: Boolean): Constant
        def unapply(constant: Constant): Option[Boolean]
      }
      val Byte: ConstantByteModule
      trait ConstantByteModule {
        def apply(x: Byte): Constant
        def unapply(constant: Constant): Option[Byte]
      }
      val Short: ConstantShortModule
      trait ConstantShortModule {
        def apply(x: Short): Constant
        def unapply(constant: Constant): Option[Short]
      }
      val Int: ConstantIntModule
      trait ConstantIntModule {
        def apply(x: Int): Constant
        def unapply(constant: Constant): Option[Int]
      }
      val Long: ConstantLongModule
      trait ConstantLongModule {
        def apply(x: Long): Constant
        def unapply(constant: Constant): Option[Long]
      }
      val Float: ConstantFloatModule
      trait ConstantFloatModule {
        def apply(x: Float): Constant
        def unapply(constant: Constant): Option[Float]
      }
      val Double: ConstantDoubleModule
      trait ConstantDoubleModule {
        def apply(x: Double): Constant
        def unapply(constant: Constant): Option[Double]
      }
      val Char: ConstantCharModule
      trait ConstantCharModule {
        def apply(x: Char): Constant
        def unapply(constant: Constant): Option[Char]
      }
      val String: ConstantStringModule
      trait ConstantStringModule {
        def apply(x: String): Constant
        def unapply(constant: Constant): Option[String]
      }
      val Unit: ConstantUnitModule
      trait ConstantUnitModule {
        def apply(): Constant
        def unapply(constant: Constant): Boolean
      }
      val Null: ConstantNullModule
      trait ConstantNullModule {
        def apply(): Constant
        def unapply(constant: Constant): Boolean
      }
      val ClassOf: ConstantClassOfModule
      trait ConstantClassOfModule {
        def apply(tpe: TypeRepr): Constant
        def unapply(constant: Constant): Option[TypeRepr]
      }
    }
    protected[quoted] val ConstantMethodsImpl: ConstantMethods
    trait ConstantMethods {
      def value(const: Constant): Any
      def showExtractors(const: Constant): String
      def show(const: Constant): String
      def showAnsiColored(const: Constant): String
    }

    // Implicits

    val Implicits: ImplicitsModule
    trait ImplicitsModule { self: Implicits.type =>
      def search(tpe: TypeRepr): ImplicitSearchResult
    }

    type ImplicitSearchResult <: AnyRef

    type ImplicitSearchSuccess <: ImplicitSearchResult
    implicit def given_TypeTest_ImplicitSearchResult_ImplicitSearchSuccess: TypeTest[ImplicitSearchResult, ImplicitSearchSuccess]
    protected[quoted] val ImplicitSearchSuccessMethodsImpl: ImplicitSearchSuccessMethods
    trait ImplicitSearchSuccessMethods {
      def tree(self: ImplicitSearchSuccess): Term
    }

    type ImplicitSearchFailure <: ImplicitSearchResult
    implicit def given_TypeTest_ImplicitSearchResult_ImplicitSearchFailure: TypeTest[ImplicitSearchResult, ImplicitSearchFailure]
    protected[quoted] val ImplicitSearchFailureMethodsImpl: ImplicitSearchFailureMethods
    trait ImplicitSearchFailureMethods {
      def explanation(self: ImplicitSearchFailure): String
    }

    type DivergingImplicit <: ImplicitSearchFailure
    implicit def given_TypeTest_ImplicitSearchResult_DivergingImplicit: TypeTest[ImplicitSearchResult, DivergingImplicit]

    type NoMatchingImplicits <: ImplicitSearchFailure
    implicit def given_TypeTest_ImplicitSearchResult_NoMatchingImplicits: TypeTest[ImplicitSearchResult, NoMatchingImplicits]

    type AmbiguousImplicits <: ImplicitSearchFailure
    implicit def given_TypeTest_ImplicitSearchResult_AmbiguousImplicits: TypeTest[ImplicitSearchResult, AmbiguousImplicits]

    // Symbol

    type Symbol <: AnyRef
    val Symbol: SymbolModule
    trait SymbolModule {
      def spliceOwner: Symbol
      def requiredPackage(path: String): Symbol
      def requiredClass(path: String): Symbol
      def requiredModule(path: String): Symbol
      def requiredMethod(path: String): Symbol
      def classSymbol(fullName: String): Symbol
      def newMethod(parent: Symbol, name: String, tpe: TypeRepr): Symbol
      def newMethod(parent: Symbol, name: String, tpe: TypeRepr, flags: Flags, privateWithin: Symbol): Symbol
      def newVal(parent: Symbol, name: String, tpe: TypeRepr, flags: Flags, privateWithin: Symbol): Symbol
      def newBind(parent: Symbol, name: String, flags: Flags, tpe: TypeRepr): Symbol
      def noSymbol: Symbol
    }
    protected[quoted] val SymbolMethodsImpl: SymbolMethods
    trait SymbolMethods {
      def owner(self: Symbol): Symbol
      def maybeOwner(self: Symbol): Symbol
      def flags(self: Symbol): Flags
      def privateWithin(self: Symbol): Option[TypeRepr]
      def protectedWithin(self: Symbol): Option[TypeRepr]
      def name(self: Symbol): String
      def fullName(self: Symbol): String
      def pos(self: Symbol): Position
      def documentation(self: Symbol): Option[Documentation]
      def tree(self: Symbol): Tree
      def annots(self: Symbol): List[Term]
      def isDefinedInCurrentRun(self: Symbol): Boolean
      def isLocalDummy(self: Symbol): Boolean
      def isRefinementClass(self: Symbol): Boolean
      def isAliasType(self: Symbol): Boolean
      def isAnonymousClass(self: Symbol): Boolean
      def isAnonymousFunction(self: Symbol): Boolean
      def isAbstractType(self: Symbol): Boolean
      def isClassConstructor(self: Symbol): Boolean
      def isType(self: Symbol): Boolean
      def isTerm(self: Symbol): Boolean
      def isPackageDef(self: Symbol): Boolean
      def isClassDef(self: Symbol): Boolean
      def isTypeDef(self: Symbol): Boolean
      def isValDef(self: Symbol): Boolean
      def isDefDef(self: Symbol): Boolean
      def isBind(self: Symbol): Boolean
      def isNoSymbol(self: Symbol): Boolean
      def exists(self: Symbol): Boolean
      def fields(self: Symbol): List[Symbol]
      def field(self: Symbol)(name: String): Symbol
      def classMethod(self: Symbol)(name: String): List[Symbol]
      def classMethods(self: Symbol): List[Symbol]
      def method(self: Symbol)(name: String): List[Symbol]
      def methods(self: Symbol): List[Symbol]
      def caseFields(self: Symbol): List[Symbol]
      def isTypeParam(self: Symbol): Boolean
      def signature(self: Symbol): Signature
      def moduleClass(self: Symbol): Symbol
      def companionClass(self: Symbol): Symbol
      def companionModule(self: Symbol): Symbol
      def showExtractors(symbol: Symbol): String
      def show(symbol: Symbol): String
      def showAnsiColored(symbol: Symbol): String
      def children(symbol: Symbol): List[Symbol]
    }

    // Signature
    type Signature <: AnyRef
    val Signature: SignatureModule
    trait SignatureModule {
      def unapply(sig: Signature): Option[(List[Any], String)]
    }
    protected[quoted] val SignatureMethodsImpl: SignatureMethods
    trait SignatureMethods {
      def paramSigs(sig: Signature): List[Any]
      def resultSig(sig: Signature): String
    }

    // Standard definitions

    val defn: DefnModule
    trait DefnModule {
      def RootPackage: Symbol
      def RootClass: Symbol
      def EmptyPackageClass: Symbol
      def ScalaPackage: Symbol
      def ScalaPackageClass: Symbol
      def AnyClass: Symbol
      def AnyValClass: Symbol
      def ObjectClass: Symbol
      def AnyRefClass: Symbol
      def NullClass: Symbol
      def NothingClass: Symbol
      def UnitClass: Symbol
      def ByteClass: Symbol
      def ShortClass: Symbol
      def CharClass: Symbol
      def IntClass: Symbol
      def LongClass: Symbol
      def FloatClass: Symbol
      def DoubleClass: Symbol
      def BooleanClass: Symbol
      def StringClass: Symbol
      def ClassClass: Symbol
      def ArrayClass: Symbol
      def PredefModule: Symbol
      def Predef_classOf: Symbol
      def JavaLangPackage: Symbol
      def ArrayModule: Symbol
      def Array_apply: Symbol
      def Array_clone: Symbol
      def Array_length: Symbol
      def Array_update: Symbol
      def RepeatedParamClass: Symbol
      def OptionClass: Symbol
      def NoneModule: Symbol
      def SomeModule: Symbol
      def ProductClass: Symbol
      def FunctionClass(arity: Int, isImplicit: Boolean = false, isErased: Boolean = false): Symbol
      def TupleClass(arity: Int): Symbol
      def isTupleClass(sym: Symbol): Boolean
      def ScalaPrimitiveValueClasses: List[Symbol]
      def ScalaNumericValueClasses: List[Symbol]
    }

    // Flags

    type Flags
    val Flags: FlagsModule
    trait FlagsModule {
      def Abstract: Flags
      def Artifact: Flags
      def Case: Flags
      def CaseAccessor: Flags
      def Contravariant: Flags
      def Covariant: Flags
      def EmptyFlags: Flags
      def Enum: Flags
      def Erased: Flags
      def ExtensionMethod: Flags
      def FiledAccessor: Flags
      def Final: Flags
      def Given: Flags
      def HasDefault: Flags
      def Implicit: Flags
      def Inline: Flags
      def JavaDefined: Flags
      def Lazy: Flags
      def Local: Flags
      def Macro: Flags
      def ModuleClass: Flags
      def Mutable: Flags
      def Object: Flags
      def Opaque: Flags
      def Open: Flags
      def Override: Flags
      def Package: Flags
      def Param: Flags
      def ParamAccessor: Flags
      def Private: Flags
      def PrivateLocal: Flags
      def Protected: Flags
      def Scala2x: Flags
      def Sealed: Flags
      def StableRealizable: Flags
      def Static: Flags
      def Synthetic: Flags
      def Trait: Flags
      def FieldAccessor: Flags
    }
    protected[quoted] val FlagsMethodsImpl: FlagsMethods
    trait FlagsMethods {
      def is(self: Flags)(that: Flags): Boolean
      def |(self: Flags)(that: Flags): Flags
      def &(self: Flags)(that: Flags): Flags
      def showExtractors(flags: Flags): String
      def show(flags: Flags): String
      def showAnsiColored(flags: Flags): String
    }

    // Positions

    type Position <: AnyRef
    val Position: PositionModule
    trait PositionModule {
      def ofMacroExpansion: Position
    }
    protected[quoted] val PositionMethodsImpl: PositionMethods
    trait PositionMethods {
      def start(pos: Position): Int
      def end(pos: Position): Int
      def exists(pos: Position): Boolean
      def sourceFile(pos: Position): SourceFile
      def startLine(pos: Position): Int
      def endLine(pos: Position): Int
      def startColumn(pos: Position): Int
      def endColumn(pos: Position): Int
      def sourceCode(pos: Position): String
    }

    type SourceFile <: AnyRef
    val SourceFile: SourceFileModule
    trait SourceFileModule
    protected[quoted] val SourceFileMethodsImpl: SourceFileMethods
    trait SourceFileMethods {
      def jpath(sourceFile: SourceFile): java.nio.file.Path
      def content(sourceFile: SourceFile): String
    }

    val Source: SourceModule
    trait SourceModule {
      def path: java.nio.file.Path
    }

    // Reporting

    val Reporting: ReportingModule
    trait ReportingModule { self: Reporting.type =>
      def error(msg: => String, pos: Position): Unit
      def error(msg: => String, source: SourceFile, start: Int, end: Int): Unit
      def warning(msg: => String, pos: Position): Unit
      def warning(msg: => String, source: SourceFile, start: Int, end: Int): Unit
    }

    // Documentation

    type Documentation <: AnyRef
    val Documentation: DocumentationModule
    trait DocumentationModule
    protected[quoted] val DocumentationMethodsImpl: DocumentationMethods
    trait DocumentationMethods {
      def raw(self: Documentation): String
      def expanded(self: Documentation): Option[String]
      def usecases(self: Documentation): List[(String, Option[DefDef])]
    }

  }

}
