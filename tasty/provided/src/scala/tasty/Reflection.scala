package scala.tasty

import scala.quoted.show.SyntaxHighlight
import scala.tasty.reflect._

// Reproduces the ABI of https://github.com/lampepfl/dotty/blob/M1/library/src/scala/tasty/Reflection.scala
// Requires the corresponding "compat" class to reproduce the API.
trait Reflection { reflectSelf =>

  type Context <: AnyRef

  // Contexts

  implicit def rootContext: Context

  // Trees

  type Tree <: AnyRef
  val Tree: TreeModule
  trait TreeModule
  protected[tasty] val TreeMethodsImpl: TreeMethods
  trait TreeMethods {
    def extension_pos(self: Tree): Position
    def extension_symbol(self: Tree): Symbol
    def extension_showExtractors(self: Tree): String
    def extension_show(self: Tree): String
    def extension_showWith(self: Tree, syntaxHighlight: SyntaxHighlight): String
    def extension_isExpr: Boolean
//    def asExprOf[T](implicit t: scala.quoted.Type[T], c: QuoteContext): scala.quoted.Expr[T]
  }

  type PackageClause <: Tree
  implicit def given_TypeTest_Tree_PackageClause: TypeTest[Tree, PackageClause]
  val PackageClause: PackageClauseModule
  trait PackageClauseModule {
    def apply(pid: Ref, stats: List[Tree]): PackageClause
    def copy(original: Tree)(pid: Ref, stats: List[Tree]): PackageClause
    def unapply(tree: PackageClause): Some[(Ref, List[Tree])]
  }
  protected[tasty] val PackageClauseMethodsImpl: PackageClauseMethods
  trait PackageClauseMethods {
    def extension_pid(self: PackageClause): Ref
    def extension_stats(self: PackageClause): List[Tree]
  }

  type Import <: Statement
  implicit def given_TypeTest_Tree_Import: TypeTest[Tree, Import]
  val Import: ImportModule
  trait ImportModule {
    def apply(expr: Term, selectors: List[ImportSelector]): Import
    def copy(original: Tree)(expr: Term, selectors: List[ImportSelector]): Import
    def unapply(tree: Import): Option[(Term, List[ImportSelector])]
  }
  protected[tasty] val ImportMethodsImpl: ImportMethods
  trait ImportMethods {
    def extension_expr(self: Import): Term
    def extension_selectors(self: Import): List[ImportSelector]
  }

  type Statement <: Tree
  implicit def given_TypeTest_Tree_Statement: TypeTest[Tree, Statement]

  type Definition <: Statement
  implicit def given_TypeTest_Tree_Definition: TypeTest[Tree, Definition]
  val Definition: DefinitionModule
  trait DefinitionModule
  protected[tasty] val DefinitionMethodsImpl: DefinitionMethods
  trait DefinitionMethods {
    def extension_name(self: Definition): String
  }

  type ClassDef <: Definition
  implicit def given_TypeTest_Tree_ClassDef: TypeTest[Tree, ClassDef]
  val ClassDef: ClassDefModule
  trait ClassDefModule {
    def copy(original: Tree)(name: String, constr: DefDef, parents: List[Tree], derived: List[TypeTree], selfOpt: Option[ValDef], body: List[Statement]): ClassDef
    def unapply(cdef: ClassDef): Option[(String, DefDef, List[Tree], List[TypeTree], Option[ValDef], List[Statement])]
  }
  protected[tasty] val ClassDefMethodsImpl: ClassDefMethods
  trait ClassDefMethods {
    def extension_constructor(self: ClassDef): DefDef
    def extension_parents(self: ClassDef): List[Tree]
    def extension_derived(self: ClassDef): List[TypeTree]
    def extension_self(self: ClassDef): Option[ValDef]
    def extension_body(self: ClassDef): List[Statement]
  }

  type DefDef <: Definition
  implicit def given_TypeTest_Tree_DefDef: TypeTest[Tree, DefDef]
  val DefDef: DefDefModule
  trait DefDefModule {
    def apply(symbol: Symbol, rhsFn: List[TypeRepr] => List[List[Term]] => Option[Term]): DefDef
    def copy(original: Tree)(name: String, typeParams: List[TypeDef], paramss: List[List[ValDef]], tpt: TypeTree, rhs: Option[Term]): DefDef
    def unapply(ddef: DefDef): Option[(String, List[TypeDef], List[List[ValDef]], TypeTree, Option[Term])]
  }
  protected[tasty] val DefDefMethodsImpl: DefDefMethods
  trait DefDefMethods {
    def extension_typeParams(self: DefDef): List[TypeDef]
    def extension_paramss(self: DefDef): List[List[ValDef]]
    def extension_returnTpt(self: DefDef): TypeTree
    def extension_rhs(self: DefDef): Option[Term]
  }

  type ValDef <: Definition
  implicit def given_TypeTest_Tree_ValDef: TypeTest[Tree, ValDef]
  val ValDef: ValDefModule
  trait ValDefModule {
    def apply(symbol: Symbol, rhs: Option[Term]): ValDef
    def copy(original: Tree)(name: String, tpt: TypeTree, rhs: Option[Term]): ValDef
    def unapply(vdef: ValDef): Option[(String, TypeTree, Option[Term])]
  }
  protected[tasty] val ValDefMethodsImpl: ValDefMethods
  trait ValDefMethods {
    def extension_tpt(self: ValDef): TypeTree
    def extension_rhs(self: ValDef): Option[Term]
  }

  type TypeDef <: Definition
  implicit def given_TypeTest_Tree_TypeDef: TypeTest[Tree, TypeDef]
  val TypeDef: TypeDefModule
  trait TypeDefModule {
    def apply(symbol: Symbol): TypeDef
    def copy(original: Tree)(name: String, rhs: Tree ): TypeDef
    def unapply(tdef: TypeDef): Option[(String, Tree  )]
  }
  protected[tasty] val TypeDefMethodsImpl: TypeDefMethods
  trait TypeDefMethods {
    def extension_rhs(self: TypeDef): Tree 
  }

  type Term <: Statement
  implicit def given_TypeTest_Tree_Term: TypeTest[Tree, Term]
  val Term: TermModule
  trait TermModule {
    def betaReduce(term: Term): Option[Term]
  }
  protected[tasty] val TermMethodsImpl: TermMethods
  trait TermMethods {
    def extension_seal(self: Term): scala.quoted.Expr[Any]
    def extension_sealOpt(self: Term): Option[scala.quoted.Expr[Any]]
    def extension_tpe(self: Term): TypeRepr
    def extension_underlyingArgument(self: Term): Term
    def extension_underlying(self: Term): Term
    def extension_etaExpand(self: Term): Term
    def extension_appliedTo(self: Term, arg: Term): Term
    def extension_appliedTo(self: Term, arg: Term, args: Term*): Term
    def extension_appliedToArgs(self: Term, args: List[Term]): Apply
    def extension_appliedToArgss(self: Term, argss: List[List[Term]]): Term
    def extension_appliedToNone(self: Term): Apply
    def extension_appliedToType(self: Term, targ: TypeRepr): Term
    def extension_appliedToTypes(self: Term, targs: List[TypeRepr]): Term
    def extension_appliedToTypeTrees(self: Term)(targs: List[TypeTree]): Term
    def extension_select(self: Term)(sym: Symbol): Select
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
  protected[tasty] val IdentMethodsImpl: IdentMethods
  trait IdentMethods {
    def extension_name(self: Ident): String
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
  protected[tasty] val SelectMethodsImpl: SelectMethods
  trait SelectMethods {
    def extension_qualifier(self: Select): Term
    def extension_name(self: Select): String
    def extension_signature(self: Select): Option[Signature]
  }

  type Literal <: Term
  implicit def given_TypeTest_Tree_Literal: TypeTest[Tree, Literal]
  val Literal: LiteralModule
  trait LiteralModule {
    def apply(constant: Constant): Literal
    def copy(original: Tree)(constant: Constant): Literal
    def unapply(x: Literal): Option[Constant]
  }
  protected[tasty] val LiteralMethodsImpl: LiteralMethods
  trait LiteralMethods {
    def extension_constant(self: Literal): Constant
  }

  type This <: Term
  implicit def given_TypeTest_Tree_This: TypeTest[Tree, This]
  val This: ThisModule
  trait ThisModule {
    def apply(cls: Symbol): This
    def copy(original: Tree)(qual: Option[String]): This
    def unapply(x: This): Option[Option[String]]
  }
  protected[tasty] val ThisMethodsImpl: ThisMethods
  trait ThisMethods {
    def extension_id(self: This): Option[String]
  }

  type New <: Term
  implicit def given_TypeTest_Tree_New: TypeTest[Tree, New]
  val New: NewModule
  trait NewModule {
    def apply(tpt: TypeTree): New
    def copy(original: Tree)(tpt: TypeTree): New
    def unapply(x: New): Option[TypeTree]
  }
  protected[tasty] val NewMethodsImpl: NewMethods
  trait NewMethods {
    def extension_tpt(self: New): TypeTree
  }

  type NamedArg <: Term
  implicit def given_TypeTest_Tree_NamedArg: TypeTest[Tree, NamedArg]
  val NamedArg: NamedArgModule
  trait NamedArgModule {
    def apply(name: String, arg: Term): NamedArg
    def copy(original: Tree)(name: String, arg: Term): NamedArg
    def unapply(x: NamedArg): Option[(String, Term)]
  }
  protected[tasty] val NamedArgMethodsImpl: NamedArgMethods
  trait NamedArgMethods {
    def extension_name(self: NamedArg): String
    def extension_value(self: NamedArg): Term
  }

  type Apply <: Term
  implicit def given_TypeTest_Tree_Apply: TypeTest[Tree, Apply]
  val Apply: ApplyModule
  trait ApplyModule {
    def apply(fun: Term, args: List[Term]): Apply
    def copy(original: Tree)(fun: Term, args: List[Term]): Apply
    def unapply(x: Apply): Option[(Term, List[Term])]
  }
  protected[tasty] val ApplyMethodsImpl: ApplyMethods
  trait ApplyMethods {
    def extension_fun(self: Apply): Term
    def extension_args(self: Apply): List[Term]
  }

  type TypeApply <: Term
  implicit def given_TypeTest_Tree_TypeApply: TypeTest[Tree, TypeApply]
  val TypeApply: TypeApplyModule
  trait TypeApplyModule {
    def apply(fun: Term, args: List[TypeTree]): TypeApply
    def copy(original: Tree)(fun: Term, args: List[TypeTree]): TypeApply
    def unapply(x: TypeApply): Option[(Term, List[TypeTree])]
  }
  protected[tasty] val TypeApplyMethodsImpl: TypeApplyMethods
  trait TypeApplyMethods {
    def extension_fun(self: TypeApply): Term
    def extension_args(self: TypeApply): List[TypeTree]
  }

  type Super <: Term
  implicit def given_TypeTest_Tree_Super: TypeTest[Tree, Super]
  val Super: SuperModule
  trait SuperModule {
    def apply(qual: Term, mix: Option[String]): Super
    def copy(original: Tree)(qual: Term, mix: Option[String]): Super
    def unapply(x: Super): Option[(Term, Option[String])]
  }
  protected[tasty] val SuperMethodsImpl: SuperMethods
  trait SuperMethods {
    def extension_qualifier(self: Super): Term
    def extension_id(self: Super): Option[String]
    def extension_idPos(self: Super): Position
  }

  type Typed <: Term
  implicit def given_TypeTest_Tree_Typed: TypeTest[Tree, Typed]
  val Typed: TypedModule
  trait TypedModule {
    def apply(expr: Term, tpt: TypeTree): Typed
    def copy(original: Tree)(expr: Term, tpt: TypeTree): Typed
    def unapply(x: Typed): Option[(Term, TypeTree)]
  }
  protected[tasty] val TypedMethodsImpl: TypedMethods
  trait TypedMethods {
    def extension_expr(self: Typed): Term
    def extension_tpt(self: Typed): TypeTree
  }

  type Assign <: Term
  implicit def given_TypeTest_Tree_Assign: TypeTest[Tree, Assign]
  val Assign: AssignModule
  trait AssignModule {
    def apply(lhs: Term, rhs: Term): Assign
    def copy(original: Tree)(lhs: Term, rhs: Term): Assign
    def unapply(x: Assign): Option[(Term, Term)]
  }
  protected[tasty] val AssignMethodsImpl: AssignMethods
  trait AssignMethods {
    def extension_lhs(self: Assign): Term
    def extension_rhs(self: Assign): Term
  }

  type Block <: Term
  implicit def given_TypeTest_Tree_Block: TypeTest[Tree, Block]
  val Block: BlockModule
  trait BlockModule {
    def apply(stats: List[Statement], expr: Term): Block
    def copy(original: Tree)(stats: List[Statement], expr: Term): Block
    def unapply(x: Block): Option[(List[Statement], Term)]
  }
  protected[tasty] val BlockMethodsImpl: BlockMethods
  trait BlockMethods {
    def extension_statements(self: Block): List[Statement]
    def extension_expr(self: Block): Term
  }

  type Closure <: Term
  implicit def given_TypeTest_Tree_Closure: TypeTest[Tree, Closure]
  val Closure: ClosureModule
  trait ClosureModule {
    def apply(meth: Term, tpt: Option[TypeRepr]): Closure
    def copy(original: Tree)(meth: Tree, tpt: Option[TypeRepr]): Closure
    def unapply(x: Closure): Option[(Term, Option[TypeRepr])]
  }
  protected[tasty] val ClosureMethodsImpl: ClosureMethods
  trait ClosureMethods {
    def extension_meth(self: Closure): Term
    def extension_tpeOpt(self: Closure): Option[TypeRepr]
  }

  val Lambda: LambdaModule
  trait LambdaModule {
    def unapply(tree: Block): Option[(List[ValDef], Term)]
    def apply(tpe: MethodType, rhsFn: List[Tree] => Tree): Block
  }

  type If <: Term
  implicit def given_TypeTest_Tree_If: TypeTest[Tree, If]
  val If: IfModule
  trait IfModule {
    def apply(cond: Term, thenp: Term, elsep: Term): If
    def copy(original: Tree)(cond: Term, thenp: Term, elsep: Term): If
    def unapply(tree: If): Option[(Term, Term, Term)]
  }
  protected[tasty] val IfMethodsImpl: IfMethods
  trait IfMethods {
    def extension_cond(self: If): Term
    def extension_thenp(self: If): Term
    def extension_elsep(self: If): Term
  }

  type Match <: Term
  implicit def given_TypeTest_Tree_Match: TypeTest[Tree, Match]
  val Match: MatchModule
  trait MatchModule {
    def apply(selector: Term, cases: List[CaseDef]): Match
    def copy(original: Tree)(selector: Term, cases: List[CaseDef]): Match
    def unapply(x: Match): Option[(Term, List[CaseDef])]
  }
  protected[tasty] val MatchMethodsImpl: MatchMethods
  trait MatchMethods {
    def extension_scrutinee(self: Match): Term
    def extension_cases(self: Match): List[CaseDef]
  }

  type GivenMatch <: Term
  implicit def given_TypeTest_Tree_GivenMatch: TypeTest[Tree, GivenMatch]
  val GivenMatch: GivenMatchModule
  trait GivenMatchModule {
    def apply(cases: List[CaseDef]): GivenMatch
    def copy(original: Tree)(cases: List[CaseDef]): GivenMatch
    def unapply(x: GivenMatch): Option[List[CaseDef]]
  }
  protected[tasty] val GivenMatchMethodsImpl: GivenMatchMethods
  trait GivenMatchMethods {
    def extension_cases(self: GivenMatch): List[CaseDef]
  }

  type Try <: Term
  implicit def given_TypeTest_Tree_Try: TypeTest[Tree, Try]
  val Try: TryModule
  trait TryModule {
    def apply(expr: Term, cases: List[CaseDef], finalizer: Option[Term]): Try
    def copy(original: Tree)(expr: Term, cases: List[CaseDef], finalizer: Option[Term]): Try
    def unapply(x: Try): Option[(Term, List[CaseDef], Option[Term])]
  }
  protected[tasty] val TryMethodsImpl: TryMethods
  trait TryMethods {
    def extension_body(self: Try): Term
    def extension_cases(self: Try): List[CaseDef]
    def extension_finalizer(self: Try): Option[Term]
  }

  type Return <: Term
  implicit def given_TypeTest_Tree_Return: TypeTest[Tree, Return]
  val Return: ReturnModule
  trait ReturnModule {
    def apply(expr: Term): Return
    def copy(original: Tree)(expr: Term): Return
    def unapply(x: Return): Option[Term]
  }
  protected[tasty] val ReturnMethodsImpl: ReturnMethods
  trait ReturnMethods {
    def extension_expr(self: Return): Term
  }

  type Repeated <: Term
  implicit def given_TypeTest_Tree_Repeated: TypeTest[Tree, Repeated]
  val Repeated: RepeatedModule
  trait RepeatedModule {
    def apply(elems: List[Term], tpt: TypeTree): Repeated
    def copy(original: Tree)(elems: List[Term], tpt: TypeTree): Repeated
    def unapply(x: Repeated): Option[(List[Term], TypeTree)]
  }
  protected[tasty] val RepeatedMethodsImpl: RepeatedMethods
  trait RepeatedMethods {
    def extension_elems(self: Repeated): List[Term]
    def extension_elemtpt(self: Repeated): TypeTree
  }

  type Inlined <: Term
  implicit def given_TypeTest_Tree_Inlined: TypeTest[Tree, Inlined]
  val Inlined: InlinedModule
  trait InlinedModule {
    def apply(call: Option[Tree ], bindings: List[Definition], expansion: Term): Inlined
    def copy(original: Tree)(call: Option[Tree ], bindings: List[Definition], expansion: Term): Inlined
    def unapply(x: Inlined): Option[(Option[Tree ], List[Definition], Term)]
  }
  protected[tasty] val InlinedMethodsImpl: InlinedMethods
  trait InlinedMethods {
    def extension_call(self: Inlined): Option[Tree ]
    def extension_bindings(self: Inlined): List[Definition]
    def extension_body(self: Inlined): Term
  }

  type SelectOuter <: Term
  implicit def given_TypeTest_Tree_SelectOuter: TypeTest[Tree, SelectOuter]
  trait SelectOuterModule {
    def apply(qualifier: Term, name: String, levels: Int): SelectOuter
    def copy(original: Tree)(qualifier: Term, name: String, levels: Int): SelectOuter
    def unapply(x: SelectOuter): Option[(Term, Int, TypeRepr)]
  }
  protected[tasty] val SelectOuterMethodsImpl: SelectOuterMethods
  trait SelectOuterMethods {
    def extension_qualifier(self: SelectOuter): Term
    def extension_name(self: SelectOuter): String
    def extension_level(self: SelectOuter): Int
  }

  type While <: Term
  implicit def given_TypeTest_Tree_While: TypeTest[Tree, While]
  val While: WhileModule
  trait WhileModule {
    def apply(cond: Term, body: Term): While
    def copy(original: Tree)(cond: Term, body: Term): While
    def unapply(x: While): Option[(Term, Term)]
  }
  protected[tasty] val WhileMethodsImpl: WhileMethods
  trait WhileMethods {
    def extension_cond(self: While): Term
    def extension_body(self: While): Term
  }

  type TypeTree <: Tree
  implicit def given_TypeTest_Tree_TypeTree: TypeTest[Tree, TypeTree]
  val TypeTree: TypeTreeModule
  trait TypeTreeModule
  protected[tasty] val TypeTreeMethodsImpl: TypeTreeMethods
  trait TypeTreeMethods {
    def extension_tpe(self: TypeTree): TypeRepr
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
  protected[tasty] val TypeIdentMethodsImpl: TypeIdentMethods
  trait TypeIdentMethods {
    def extension_name(self: TypeIdent): String
  }

  type TypeSelect <: TypeTree
  implicit def given_TypeTest_Tree_TypeSelect: TypeTest[Tree, TypeSelect]
  val TypeSelect: TypeSelectModule
  trait TypeSelectModule {
    def apply(qualifier: Term, name: String): TypeSelect
    def copy(original: Tree)(qualifier: Term, name: String): TypeSelect
    def unapply(x: TypeSelect): Option[(Term, String)]
  }
  protected[tasty] val TypeSelectMethodsImpl: TypeSelectMethods
  trait TypeSelectMethods {
    def extension_qualifier(self: TypeSelect): Term
    def extension_name(self: TypeSelect): String
  }

  type Projection <: TypeTree
  implicit def given_TypeTest_Tree_Projection: TypeTest[Tree, Projection]
  val Projection: ProjectionModule
  trait ProjectionModule {
    def copy(original: Tree)(qualifier: TypeTree, name: String): Projection
    def unapply(x: Projection): Option[(TypeTree, String)]
  }
  protected[tasty] val ProjectionMethodsImpl: ProjectionMethods
  trait ProjectionMethods {
    def extension_qualifier(self: Projection): TypeTree
    def extension_name(self: Projection): String
  }

  type Singleton <: TypeTree
  implicit def given_TypeTest_Tree_Singleton: TypeTest[Tree, Singleton]
  val Singleton: SingletonModule
  trait SingletonModule {
    def apply(ref: Term): Singleton
    def copy(original: Tree)(ref: Term): Singleton
    def unapply(x: Singleton): Option[Term]
  }
  protected[tasty] val SingletonMethodsImpl: SingletonMethods
  trait SingletonMethods {
    def extension_ref(self: Singleton): Term
  }

  type Refined <: TypeTree
  implicit def given_TypeTest_Tree_Refined: TypeTest[Tree, Refined]
  val Refined: RefinedModule
  protected[tasty] val RefinedMethodsImpl: RefinedMethods
  trait RefinedModule {
    def copy(original: Tree)(tpt: TypeTree, refinements: List[Definition]): Refined
    def unapply(x: Refined): Option[(TypeTree, List[Definition])]
  }
  trait RefinedMethods {
    def extension_tpt(self: Refined): TypeTree
    def extension_refinements(self: Refined): List[Definition]
  }

  type Applied <: TypeTree
  implicit def given_TypeTest_Tree_Applied: TypeTest[Tree, Applied]
  val Applied: AppliedModule
  trait AppliedModule {
    def apply(tpt: TypeTree, args: List[Tree ]): Applied
    def copy(original: Tree)(tpt: TypeTree, args: List[Tree ]): Applied
    def unapply(x: Applied): Option[(TypeTree, List[Tree ])]
  }
  protected[tasty] val AppliedMethodsImpl: AppliedMethods
  trait AppliedMethods {
    def extension_tpt(self: Applied): TypeTree
    def extension_args(self: Applied): List[Tree ]
  }

  type Annotated <: TypeTree
  implicit def given_TypeTest_Tree_Annotated: TypeTest[Tree, Annotated]
  val Annotated: AnnotatedModule
  trait AnnotatedModule {
    def apply(arg: TypeTree, annotation: Term): Annotated
    def copy(original: Tree)(arg: TypeTree, annotation: Term): Annotated
    def unapply(x: Annotated): Option[(TypeTree, Term)]
  }
  protected[tasty] val AnnotatedMethodsImpl: AnnotatedMethods
  trait AnnotatedMethods {
    def extension_arg(self: Annotated): TypeTree
    def extension_annotation(self: Annotated): Term
  }

  type MatchTypeTree <: TypeTree
  implicit def given_TypeTest_Tree_MatchTypeTree: TypeTest[Tree, MatchTypeTree]
  val MatchTypeTree: MatchTypeTreeModule
  trait MatchTypeTreeModule {
    def apply(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef]): MatchTypeTree
    def copy(original: Tree)(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef]): MatchTypeTree
    def unapply(x: MatchTypeTree): Option[(Option[TypeTree], TypeTree, List[TypeCaseDef])]
  }
  protected[tasty] val MatchTypeTreeMethodsImpl: MatchTypeTreeMethods
  trait MatchTypeTreeMethods {
    def extension_bound(self: MatchTypeTree): Option[TypeTree]
    def extension_selector(self: MatchTypeTree): TypeTree
    def extension_cases(self: MatchTypeTree): List[TypeCaseDef]
  }

  type ByName <: TypeTree
  implicit def given_TypeTest_Tree_ByName: TypeTest[Tree, ByName]
  val ByName: ByNameModule
  trait ByNameModule {
    def apply(result: TypeTree): ByName
    def copy(original: Tree)(result: TypeTree): ByName
    def unapply(x: ByName): Option[TypeTree]
  }
  protected[tasty] val ByNameMethodsImpl: ByNameMethods
  trait ByNameMethods {
    def extension_result(self: ByName): TypeTree
  }

  type LambdaTypeTree <: TypeTree
  implicit def given_TypeTest_Tree_LambdaTypeTree: TypeTest[Tree, LambdaTypeTree]
  val LambdaTypeTree: LambdaTypeTreeModule
  trait LambdaTypeTreeModule {
    def apply(tparams: List[TypeDef], body: Tree ): LambdaTypeTree
    def copy(original: Tree)(tparams: List[TypeDef], body: Tree ): LambdaTypeTree
    def unapply(tree: LambdaTypeTree): Option[(List[TypeDef], Tree )]
  }
  protected[tasty] val LambdaTypeTreeMethodsImpl: LambdaTypeTreeMethods
  trait LambdaTypeTreeMethods {
    def extension_tparams(self: LambdaTypeTree): List[TypeDef]
    def extension_body(self: LambdaTypeTree): Tree 
  }

  type TypeBind <: TypeTree
  implicit def given_TypeTest_Tree_TypeBind: TypeTest[Tree, TypeBind]
  val TypeBind: TypeBindModule
  trait TypeBindModule {
    def copy(original: Tree)(name: String, tpt: Tree ): TypeBind
    def unapply(x: TypeBind): Option[(String, Tree )]
  }
  protected[tasty] val TypeBindMethodsImpl: TypeBindMethods
  trait TypeBindMethods {
    def extension_name(self: TypeBind): String
    def extension_body(self: TypeBind): Tree 
  }

  type TypeBlock <: TypeTree
  implicit def given_TypeTest_Tree_TypeBlock: TypeTest[Tree, TypeBlock]
  val TypeBlock: TypeBlockModule
  trait TypeBlockModule {
    def apply(aliases: List[TypeDef], tpt: TypeTree): TypeBlock
    def copy(original: Tree)(aliases: List[TypeDef], tpt: TypeTree): TypeBlock
    def unapply(x: TypeBlock): Option[(List[TypeDef], TypeTree)]
  }
  protected[tasty] val TypeBlockMethodsImpl: TypeBlockMethods
  trait TypeBlockMethods {
    def extension_aliases(self: TypeBlock): List[TypeDef]
    def extension_tpt(self: TypeBlock): TypeTree
  }

  type TypeBoundsTree <: Tree /*TypeTree | TypeBoundsTree*/
  implicit def given_TypeTest_Tree_TypeBoundsTree: TypeTest[Tree, TypeBoundsTree]
  val TypeBoundsTree: TypeBoundsTreeModule
  trait TypeBoundsTreeModule {
    def unapply(x: TypeBoundsTree): Option[(TypeTree, TypeTree)]
  }
  protected[tasty] val TypeBoundsTreeMethodsImpl: TypeBoundsTreeMethods
  trait TypeBoundsTreeMethods {
    def extension_tpe(self: TypeBoundsTree): TypeBounds
    def extension_low(self: TypeBoundsTree): TypeTree
    def extension_hi(self: TypeBoundsTree): TypeTree
  }

  type WildcardTypeTree  <: Tree
  implicit def given_TypeTest_Tree_WildcardTypeTree: TypeTest[Tree, WildcardTypeTree]
  val WildcardTypeTree: WildcardTypeTreeModule
  trait WildcardTypeTreeModule {
    def unapply(x: WildcardTypeTree): Boolean
  }
  protected[tasty] val WildcardTypeTreeMethodsImpl: WildcardTypeTreeMethods
  trait WildcardTypeTreeMethods {
    def extension_tpe(self: WildcardTypeTree): TypeRepr
  }

  type CaseDef <: Tree
  implicit def given_TypeTest_Tree_CaseDef: TypeTest[Tree, CaseDef]
  val CaseDef: CaseDefModule
  trait CaseDefModule {
    def apply(pattern: Tree, guard: Option[Term], rhs: Term): CaseDef
    def copy(original: Tree)(pattern: Tree, guard: Option[Term], rhs: Term): CaseDef
    def unapply(x: CaseDef): Option[(Tree, Option[Term], Term)]
  }
  protected[tasty] val CaseDefMethodsImpl: CaseDefMethods
  trait CaseDefMethods {
    def extension_pattern(caseDef: CaseDef): Tree
    def extension_guard(caseDef: CaseDef): Option[Term]
    def extension_rhs(caseDef: CaseDef): Term
  }

  type TypeCaseDef <: Tree
  implicit def given_TypeTest_Tree_TypeCaseDef: TypeTest[Tree, TypeCaseDef]
  val TypeCaseDef: TypeCaseDefModule
  trait TypeCaseDefModule {
    def apply(pattern: TypeTree, rhs: TypeTree): TypeCaseDef
    def copy(original: Tree)(pattern: TypeTree, rhs: TypeTree): TypeCaseDef
    def unapply(tree: TypeCaseDef): Option[(TypeTree, TypeTree)]
  }
  protected[tasty] val TypeCaseDefMethodsImpl: TypeCaseDefMethods
  trait TypeCaseDefMethods {
    def extension_pattern(caseDef: TypeCaseDef): TypeTree
    def extension_rhs(caseDef: TypeCaseDef): TypeTree
  }

  type Bind <: Tree
  implicit def given_TypeTest_Tree_Bind: TypeTest[Tree, Bind]
  val Bind: BindModule
  trait BindModule {
    def copy(original: Tree)(name: String, pattern: Tree): Bind
    def unapply(pattern: Bind): Option[(String, Tree)]
  }
  protected[tasty] val BindMethodsImpl: BindMethods
  trait BindMethods {
    def extension_name(bind: Bind): String
    def extension_pattern(bind: Bind): Tree
  }

  type Unapply <: Tree
  implicit def given_TypeTest_Tree_Unapply: TypeTest[Tree, Unapply]
  val Unapply: UnapplyModule
  trait UnapplyModule {
    def copy(original: Tree)(fun: Term, implicits: List[Term], patterns: List[Tree]): Unapply
    def unapply(x: Unapply): Option[(Term, List[Term], List[Tree])]
  }
  protected[tasty] val UnapplyMethodsImpl: UnapplyMethods
  trait UnapplyMethods {
    def extension_fun(unapply: Unapply): Term
    def extension_implicits(unapply: Unapply): List[Term]
    def extension_patterns(unapply: Unapply): List[Tree]
  }

  type Alternatives <: Tree
  implicit def given_TypeTest_Tree_Alternatives: TypeTest[Tree, Alternatives]
  val Alternatives: AlternativesModule
  trait AlternativesModule {
    def apply(patterns: List[Tree]): Alternatives
    def copy(original: Tree)(patterns: List[Tree]): Alternatives
    def unapply(x: Alternatives): Option[List[Tree]]
  }
  protected[tasty] val AlternativesMethodsImpl: AlternativesMethods
  trait AlternativesMethods {
    def extension_patterns(alternatives: Alternatives): List[Tree]
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
  protected[tasty] val SimpleSelectorMethodsImpl: SimpleSelectorMethods
  trait SimpleSelectorMethods {
    def extension_name(self: SimpleSelector): String
    def extension_namePos(self: SimpleSelector): Position
  }

  type RenameSelector <: ImportSelector
  implicit def given_TypeTest_ImportSelector_RenameSelector: TypeTest[ImportSelector, RenameSelector]
  val RenameSelector: RenameSelectorModule
  trait RenameSelectorModule {
    def unapply(x: RenameSelector): Option[(String, String)]
  }
  protected[tasty] val RenameSelectorMethodsImpl: RenameSelectorMethods
  trait RenameSelectorMethods {
    def extension_fromName(self: RenameSelector): String
    def extension_fromPos(self: RenameSelector): Position
    def extension_toName(self: RenameSelector): String
    def extension_toPos(self: RenameSelector): Position
  }

  type OmitSelector <: ImportSelector
  implicit def given_TypeTest_ImportSelector_OmitSelector: TypeTest[ImportSelector, OmitSelector]
  val OmitSelector: OmitSelectorModule
  trait OmitSelectorModule {
    def unapply(x: OmitSelector): Option[String]
  }
  protected[tasty] val OmitSelectorMethodsImpl: OmitSelectorMethods
  trait OmitSelectorMethods {
    def extension_name(self: OmitSelector): String
    def extension_namePos(self: OmitSelector): Position
  }

  // Types

//  def typeOf[T](implicit qtype: scala.quoted.Type[T], ctx: Context): Type

  type TypeRepr
  val TypeRepr: TypeModule
  trait TypeModule {
    def of[T](implicit qtype: scala.quoted.Type[T]): TypeRepr
    def typeConstructorOf(clazz: Class[_]): TypeRepr
  }
  protected[tasty] val TypeMethodsImpl: TypeMethods
  trait TypeMethods {
    def extension_showExtractors(self: TypeRepr): String
    def extension_show(self: TypeRepr): String
    def extension_showWith(self: TypeRepr, syntaxHighlight: SyntaxHighlight): String
    def extension_seal(self: TypeRepr): scala.quoted.Type[_]
    def extension_=:=(self: TypeRepr)(that: TypeRepr): Boolean
    def extension_<:<(self: TypeRepr)(that: TypeRepr): Boolean
    def extension_widen(self: TypeRepr): TypeRepr
    def extension_widenTermRefExpr(self: TypeRepr): TypeRepr
    def extension_dealias(self: TypeRepr): TypeRepr
    def extension_simplified(self: TypeRepr): TypeRepr
    def extension_classSymbol(self: TypeRepr): Option[Symbol]
    def extension_typeSymbol(self: TypeRepr): Symbol
    def extension_termSymbol(self: TypeRepr): Symbol
    def extension_isSingleton(self: TypeRepr): Boolean
    def extension_memberType(self: TypeRepr)(member: Symbol): TypeRepr
    def extension_baseClasses(self: TypeRepr): List[Symbol]
    def extension_baseType(self: TypeRepr)(member: Symbol): TypeRepr
    def extension_derivesFrom(self: TypeRepr)(cls: Symbol): Boolean
    def extension_isFunctionType(self: TypeRepr): Boolean
    def extension_isContextFunctionType(self: TypeRepr): Boolean
    def extension_isErasedFunctionType(self: TypeRepr): Boolean
    def extension_isDependentFunctionType(self: TypeRepr): Boolean
    def extension_select(self: TypeRepr)(sym: Symbol): TypeRepr
    def extension_appliedTo(self: TypeRepr, targ: TypeRepr): TypeRepr
    def extension_appliedTo(self: TypeRepr, targs: List[TypeRepr]): TypeRepr
  }

  type ConstantType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_ConstantType: TypeTest[TypeRepr, ConstantType]
  val ConstantType: ConstantTypeModule
  trait ConstantTypeModule {
    def apply(x : Constant): ConstantType
    def unapply(x: ConstantType): Option[Constant]
  }
  protected[tasty] val ConstantTypeMethodsImpl: ConstantTypeMethods
  trait ConstantTypeMethods {
    def extension_constant(self: ConstantType): Constant
  }

  type TermRef <: TypeRepr
  implicit def given_TypeTest_TypeRepr_TermRef: TypeTest[TypeRepr, TermRef]
  val TermRef: TermRefModule
  trait TermRefModule {
    def apply(qual: TypeRepr, name: String): TermRef
    def unapply(x: TermRef): Option[(TypeRepr , String)]
  }
  protected[tasty] val TermRefMethodsImpl: TermRefMethods
  trait TermRefMethods {
    def extension_qualifier(self: TermRef): TypeRepr 
    def extension_name(self: TermRef): String
  }

  type TypeRef <: TypeRepr
  implicit def given_TypeTest_TypeRepr_TypeRef: TypeTest[TypeRepr, TypeRef]
  val TypeRef: TypeRefModule
  trait TypeRefModule {
    def unapply(x: TypeRef): Option[(TypeRepr, String)]
  }
  protected[tasty] val TypeRefMethodsImpl: TypeRefMethods
  trait TypeRefMethods {
    def extension_qualifier(self: TypeRef): TypeRepr 
    def extension_name(self: TypeRef): String
    def extension_isOpaqueAlias(self: TypeRef): Boolean
    def extension_translucentSuperType(self: TypeRef): TypeRepr
  }

  type SuperType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_SuperType: TypeTest[TypeRepr, SuperType]
  val SuperType: SuperTypeModule
  trait SuperTypeModule {
    def apply(thistpe: TypeRepr, supertpe: TypeRepr): SuperType
    def unapply(x: SuperType): Option[(TypeRepr, TypeRepr)]
  }
  protected[tasty] val SuperTypeMethodsImpl: SuperTypeMethods
  trait SuperTypeMethods {
    def extension_thistpe(self: SuperType): TypeRepr
    def extension_supertpe(self: SuperType): TypeRepr
  }

  type Refinement <: TypeRepr
  implicit def given_TypeTest_TypeRepr_Refinement: TypeTest[TypeRepr, Refinement]
  val Refinement: RefinementModule
  trait RefinementModule {
    def apply(parent: TypeRepr, name: String, info: TypeRepr): Refinement
    def unapply(x: Refinement): Option[(TypeRepr, String, TypeRepr )]
  }
  protected[tasty] val RefinementMethodsImpl: RefinementMethods
  trait RefinementMethods {
    def extension_parent(self: Refinement): TypeRepr
    def extension_name(self: Refinement): String
    def extension_info(self: Refinement): TypeRepr
  }

  type AppliedType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_AppliedType: TypeTest[TypeRepr, AppliedType]
  val AppliedType: AppliedTypeModule
  trait AppliedTypeModule {
    def apply(tycon: TypeRepr, args: List[TypeRepr]): AppliedType
    def unapply(x: AppliedType): Option[(TypeRepr, List[TypeRepr ])]
  }
  protected[tasty] val AppliedTypeMethodsImpl: AppliedTypeMethods
  trait AppliedTypeMethods {
    def extension_tycon(self: AppliedType): TypeRepr
    def extension_args(self: AppliedType): List[TypeRepr]
  }

  type AnnotatedType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_AnnotatedType: TypeTest[TypeRepr, AnnotatedType]
  val AnnotatedType: AnnotatedTypeModule
  trait AnnotatedTypeModule {
    def apply(underlying: TypeRepr, annot: Term): AnnotatedType
    def unapply(x: AnnotatedType): Option[(TypeRepr, Term)]
  }
  protected[tasty] val AnnotatedTypeMethodsImpl: AnnotatedTypeMethods
  trait AnnotatedTypeMethods {
    def extension_underlying(self: AnnotatedType): TypeRepr
    def extension_annot(self: AnnotatedType): Term
  }

  type AndType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_AndType: TypeTest[TypeRepr, AndType]
  val AndType: AndTypeModule
  trait AndTypeModule {
    def apply(lhs: TypeRepr, rhs: TypeRepr): AndType
    def unapply(x: AndType): Option[(TypeRepr, TypeRepr)]
  }
  protected[tasty] val AndTypeMethodsImpl: AndTypeMethods
  trait AndTypeMethods {
    def extension_left(self: AndType): TypeRepr
    def extension_right(self: AndType): TypeRepr
  }

  type OrType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_OrType: TypeTest[TypeRepr, OrType]
  val OrType: OrTypeModule
  trait OrTypeModule {
    def apply(lhs: TypeRepr, rhs: TypeRepr): OrType
    def unapply(x: OrType): Option[(TypeRepr, TypeRepr)]
  }
  protected[tasty] val OrTypeMethodsImpl: OrTypeMethods
  trait OrTypeMethods {
    def extension_left(self: OrType): TypeRepr
    def extension_right(self: OrType): TypeRepr
  }

  type MatchType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_MatchType: TypeTest[TypeRepr, MatchType]
  val MatchType: MatchTypeModule
  trait MatchTypeModule {
    def apply(bound: TypeRepr, scrutinee: TypeRepr, cases: List[TypeRepr]): MatchType
    def unapply(x: MatchType): Option[(TypeRepr, TypeRepr, List[TypeRepr])]
  }
  protected[tasty] val MatchTypeMethodsImpl: MatchTypeMethods
  trait MatchTypeMethods {
    def extension_bound(self: MatchType): TypeRepr
    def extension_scrutinee(self: MatchType): TypeRepr
    def extension_cases(self: MatchType): List[TypeRepr]
  }

//  def MatchCaseType: Type

  type ByNameType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_ByNameType: TypeTest[TypeRepr, ByNameType]
  val ByNameType: ByNameTypeModule
  trait ByNameTypeModule {
    def apply(underlying: TypeRepr): TypeRepr
    def unapply(x: ByNameType): Option[TypeRepr]
  }
  protected[tasty] val ByNameTypeMethodsImpl: ByNameTypeMethods
  trait ByNameTypeMethods {
    def extension_underlying(self: ByNameType): TypeRepr
  }

  type ParamRef <: TypeRepr
  implicit def given_TypeTest_TypeRepr_ParamRef: TypeTest[TypeRepr, ParamRef]
  val ParamRef: ParamRefModule
  trait ParamRefModule {
    def unapply(x: ParamRef): Option[(LambdaType, Int)]
  }
  protected[tasty] val ParamRefMethodsImpl: ParamRefMethods
  trait ParamRefMethods {
    def extension_binder(self: ParamRef): LambdaType
    def extension_paramNum(self: ParamRef): Int
  }

  type ThisType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_ThisType: TypeTest[TypeRepr, ThisType]
  val ThisType: ThisTypeModule
  trait ThisTypeModule {
    def unapply(x: ThisType): Option[TypeRepr]
  }
  protected[tasty] val ThisTypeMethodsImpl: ThisTypeMethods
  trait ThisTypeMethods {
    def extension_tref(self: ThisType): TypeRepr
  }

  type RecursiveThis <: TypeRepr
  implicit def given_TypeTest_TypeRepr_RecursiveThis: TypeTest[TypeRepr, RecursiveThis]
  val RecursiveThis: RecursiveThisModule
  trait RecursiveThisModule {
    def unapply(x: RecursiveThis): Option[RecursiveType]
  }
  protected[tasty] val RecursiveThisMethodsImpl: RecursiveThisMethods
  trait RecursiveThisMethods {
    def extension_binder(self: RecursiveThis): RecursiveType
  }

  type RecursiveType <: TypeRepr
  implicit def given_TypeTest_TypeRepr_RecursiveType: TypeTest[TypeRepr, RecursiveType]
  val RecursiveType: RecursiveTypeModule
  trait RecursiveTypeModule {
    def apply(parentExp: RecursiveType => TypeRepr): RecursiveType
    def unapply(x: RecursiveType): Option[TypeRepr]
  }
  protected[tasty] val RecursiveTypeMethodsImpl: RecursiveTypeMethods
  trait RecursiveTypeMethods {
    def extension_underlying(self: RecursiveType): TypeRepr
    def extension_recThis(self: RecursiveType): RecursiveThis
  }

  type LambdaType <: TypeRepr

  type MethodType <: LambdaType
  implicit def given_TypeTest_TypeRepr_MethodType: TypeTest[TypeRepr, MethodType]
  val MethodType: MethodTypeModule
  trait MethodTypeModule {
    def apply(paramNames: List[String])(paramInfosExp: MethodType => List[TypeRepr], resultTypeExp: MethodType => TypeRepr): MethodType
    def unapply(x: MethodType): Option[(List[String], List[TypeRepr], TypeRepr)]
  }
  protected[tasty] val MethodTypeMethodsImpl: MethodTypeMethods
  trait MethodTypeMethods {
    def extension_isImplicit(self: MethodType): Boolean
    def extension_isErased(self: MethodType): Boolean
    def extension_param(self: MethodType)(idx: Int): TypeRepr
    def extension_paramNames(self: MethodType): List[String]
    def extension_paramTypes(self: MethodType): List[TypeRepr]
    def extension_resType(self: MethodType): TypeRepr
  }

  type PolyType <: LambdaType
  implicit def given_TypeTest_TypeRepr_PolyType: TypeTest[TypeRepr, PolyType]
  val PolyType: PolyTypeModule
  trait PolyTypeModule {
    def apply(paramNames: List[String])(paramBoundsExp: PolyType => List[TypeBounds], resultTypeExp: PolyType => TypeRepr): PolyType
    def unapply(x: PolyType): Option[(List[String], List[TypeBounds], TypeRepr)]
  }
  protected[tasty] val PolyTypeMethodsImpl: PolyTypeMethods
  trait PolyTypeMethods {
    def extension_param(self: PolyType)(idx: Int): TypeRepr
    def extension_paramNames(self: PolyType): List[String]
    def extension_paramBounds(self: PolyType): List[TypeBounds]
    def extension_resType(self: PolyType): TypeRepr
  }

  type TypeLambda <: LambdaType
  implicit def given_TypeTest_TypeRepr_TypeLambda: TypeTest[TypeRepr, TypeLambda]
  val TypeLambda: TypeLambdaModule
  trait TypeLambdaModule {
    def apply(paramNames: List[String], boundsFn: TypeLambda => List[TypeBounds], bodyFn: TypeLambda => TypeRepr): TypeLambda
    def unapply(x: TypeLambda): Option[(List[String], List[TypeBounds], TypeRepr)]
  }
  protected[tasty] val TypeLambdaMethodsImpl: TypeLambdaMethods
  trait TypeLambdaMethods {
    def extension_paramNames(self: TypeLambda): List[String]
    def extension_paramBounds(self: TypeLambda): List[TypeBounds]
    def extension_param(self: TypeLambda)(idx: Int) : TypeRepr
    def extension_resType(self: TypeLambda): TypeRepr
  }

  type TypeBounds <: TypeRepr
  implicit def given_TypeTest_TypeRepr_TypeBounds: TypeTest[TypeRepr, TypeBounds]
  val TypeBounds: TypeBoundsModule
  trait TypeBoundsModule {
    def apply(low: TypeRepr, hi: TypeRepr): TypeBounds
    def unapply(x: TypeBounds): Option[(TypeRepr, TypeRepr)]
  }
  protected[tasty] val TypeBoundsMethodsImpl: TypeBoundsMethods
  trait TypeBoundsMethods {
    def extension_low(self: TypeBounds): TypeRepr
    def extension_hi(self: TypeBounds): TypeRepr
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
  protected[tasty] val ConstantMethodsImpl: ConstantMethods
  trait ConstantMethods {
    def extension_value(const: Constant): Any
    def extension_showExtractors(const: Constant): String
    def extension_show(const: Constant): String
    def extension_showWith(const: Constant, syntaxHighlight: SyntaxHighlight): String
  }

  // Implicits

  val Implicits: ImplicitsModule
  trait ImplicitsModule { self: Implicits.type =>
    def search(tpe: TypeRepr): ImplicitSearchResult
  }

  type ImplicitSearchResult <: AnyRef

  type ImplicitSearchSuccess <: ImplicitSearchResult
  implicit def given_TypeTest_ImplicitSearchResult_ImplicitSearchSuccess: TypeTest[ImplicitSearchResult, ImplicitSearchSuccess]
  protected[tasty] val ImplicitSearchSuccessMethodsImpl: ImplicitSearchSuccessMethods
  trait ImplicitSearchSuccessMethods {
    def extension_tree(self: ImplicitSearchSuccess): Term
  }

  type ImplicitSearchFailure <: ImplicitSearchResult
  implicit def given_TypeTest_ImplicitSearchResult_ImplicitSearchFailure: TypeTest[ImplicitSearchResult, ImplicitSearchFailure]
  protected[tasty] val ImplicitSearchFailureMethodsImpl: ImplicitSearchFailureMethods
  trait ImplicitSearchFailureMethods {
    def extension_explanation(self: ImplicitSearchFailure): String
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
    def currentOwner: Symbol
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
  protected[tasty] val SymbolMethodsImpl: SymbolMethods
  trait SymbolMethods {
    def extension_owner(self: Symbol): Symbol
    def extension_maybeOwner(self: Symbol): Symbol
    def extension_flags(self: Symbol): Flags
    def extension_privateWithin(self: Symbol): Option[TypeRepr]
    def extension_protectedWithin(self: Symbol): Option[TypeRepr]
    def extension_name(self: Symbol): String
    def extension_fullName(self: Symbol): String
    def extension_pos(self: Symbol): Position
    def extension_localContext(self: Symbol): Context
    def extension_documentation(self: Symbol): Option[Documentation]
    def extension_tree(self: Symbol): Tree
    def extension_annots(self: Symbol): List[Term]
    def extension_isDefinedInCurrentRun(self: Symbol): Boolean
    def extension_isLocalDummy(self: Symbol): Boolean
    def extension_isRefinementClass(self: Symbol): Boolean
    def extension_isAliasType(self: Symbol): Boolean
    def extension_isAnonymousClass(self: Symbol): Boolean
    def extension_isAnonymousFunction(self: Symbol): Boolean
    def extension_isAbstractType(self: Symbol): Boolean
    def extension_isClassConstructor(self: Symbol): Boolean
    def extension_isType(self: Symbol): Boolean
    def extension_isTerm(self: Symbol): Boolean
    def extension_isPackageDef(self: Symbol): Boolean
    def extension_isClassDef(self: Symbol): Boolean
    def extension_isTypeDef(self: Symbol): Boolean
    def extension_isValDef(self: Symbol): Boolean
    def extension_isDefDef(self: Symbol): Boolean
    def extension_isBind(self: Symbol): Boolean
    def extension_isNoSymbol(self: Symbol): Boolean
    def extension_exists(self: Symbol): Boolean
    def extension_fields(self: Symbol): List[Symbol]
    def extension_field(self: Symbol)(name: String): Symbol
    def extension_classMethod(self: Symbol)(name: String): List[Symbol]
    def extension_classMethods(self: Symbol): List[Symbol]
    def extension_method(self: Symbol)(name: String): List[Symbol]
    def extension_methods(self: Symbol): List[Symbol]
    def extension_caseFields(self: Symbol): List[Symbol]
    def extension_isTypeParam(self: Symbol): Boolean
    def extension_signature(self: Symbol): Signature
    def extension_moduleClass(self: Symbol): Symbol
    def extension_companionClass(self: Symbol): Symbol
    def extension_companionModule(self: Symbol): Symbol
    def extension_showExtractors(symbol: Symbol): String
    def extension_show(symbol: Symbol): String
    def extension_showWith(symbol: Symbol, syntaxHighlight: SyntaxHighlight): String
    def extension_children(symbol: Symbol): List[Symbol]
  }

  // Signature
  type Signature <: AnyRef
  val Signature: SignatureModule
  trait SignatureModule {
    def unapply(sig: Signature): Option[(List[Any], String)]
  }
  protected[tasty] val SignatureMethodsImpl: SignatureMethods
  trait SignatureMethods {
    def extension_paramSigs(sig: Signature): List[Any]
    def extension_resultSig(sig: Signature): String
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
  protected[tasty] val FlagsMethodsImpl: FlagsMethods
  trait FlagsMethods {
    def extension_is(self: Flags)(that: Flags): Boolean
    def extension_|(self: Flags)(that: Flags): Flags
    def extension_&(self: Flags)(that: Flags): Flags
    def extension_showExtractors(flags: Flags): String
    def extension_show(flags: Flags): String
    def extension_showWith(flags: Flags, syntaxHighlight: SyntaxHighlight): String
  }

  // Positions

  def rootPosition: Position

  type Position <: AnyRef
  val Position: PositionModule
  trait PositionModule
  protected[tasty] val PositionMethodsImpl: PositionMethods
  trait PositionMethods {
    def extension_start(pos: Position): Int
    def extension_end(pos: Position): Int
    def extension_exists(pos: Position): Boolean
    def extension_sourceFile(pos: Position): SourceFile
    def extension_startLine(pos: Position): Int
    def extension_endLine(pos: Position): Int
    def extension_startColumn(pos: Position): Int
    def extension_endColumn(pos: Position): Int
    def extension_sourceCode(pos: Position): String
  }

  type SourceFile <: AnyRef
  val SourceFile: SourceFileModule
  trait SourceFileModule
  protected[tasty] val SourceFileMethodsImpl: SourceFileMethods
  trait SourceFileMethods {
    def extension_jpath(sourceFile: SourceFile): java.nio.file.Path
    def extension_content(sourceFile: SourceFile): String
  }

  val Source: SourceModule
  trait SourceModule {
    def path: java.nio.file.Path
    def isJavaCompilationUnit: Boolean
    def isScala2CompilationUnit: Boolean
    def isAlreadyLoadedCompilationUnit: Boolean
    def compilationUnitClassname: String
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
  protected[tasty] val DocumentationMethodsImpl: DocumentationMethods
  trait DocumentationMethods {
    def extension_raw(self: Documentation): String
    def extension_expanded(self: Documentation): Option[String]
    def extension_usecases(self: Documentation): List[(String, Option[DefDef])]
  }

}
