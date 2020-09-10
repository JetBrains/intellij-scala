package scala.tasty

import scala.internal.CompilerInterface
import scala.quoted.show.SyntaxHighlight
import scala.tasty.reflect._

// Reproduces the ABI of https://github.com/lampepfl/dotty/blob/0.27.0-RC1/library/src/scala/tasty/Reflection.scala
// Requires the corresponding "compat" class to reproduce the API.
trait Reflection extends CompilerInterface { reflectSelf =>

  // Contexts

  def rootContext: Context = ???

  // Source

  object Source {
    def path(implicit ctx: Context): java.nio.file.Path = ???
    def isJavaCompilationUnit(implicit ctx: Context): Boolean = ???
    def isScala2CompilationUnit(implicit ctx: Context): Boolean = ???
    def isAlreadyLoadedCompilationUnit(implicit ctx: Context): Boolean = ???
    def compilationUnitClassname(implicit ctx: Context): String = ???
  }

  // Trees

  object Tree {
    def extension_pos(self: Tree)(implicit ctx: Context): Position = ???
    def extension_symbol(self: Tree)(implicit ctx: Context): Symbol = ???
    def extension_showExtractors(self: Tree)(implicit ctx: Context): String = ???
    def extension_show(self: Tree)(implicit ctx: Context): String = ???
    def extension_showWith(self: Tree, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
    def extension_isExpr(implicit ctx: Context): Boolean = ???
//    def asExprOf[T](implicit t: scala.quoted.Type[T], c: QuoteContext): scala.quoted.Expr[T] = ???
  }
  implicit def given_TypeTest_Tree_PackageClause(implicit v: Context): TypeTest[Tree, PackageClause] = ???
  object PackageClause {
    def apply(pid: Ref, stats: List[Tree])(implicit ctx: Context): PackageClause = ???
    def copy(original: Tree)(pid: Ref, stats: List[Tree])(implicit ctx: Context): PackageClause = ???
    def unapply(tree: PackageClause)(implicit ctx: Context): Some[(Ref, List[Tree])] = ???

    def extension_pid(self: PackageClause)(implicit ctx: Context): Ref = ???
    def extension_stats(self: PackageClause)(implicit ctx: Context): List[Tree] = ???
  }
  implicit def given_TypeTest_Tree_Import(implicit v: Context): TypeTest[Tree, Import] = ???
  object Import {
    def apply(expr: Term, selectors: List[ImportSelector])(implicit ctx: Context): Import = ???
    def copy(original: Tree)(expr: Term, selectors: List[ImportSelector])(implicit ctx: Context): Import = ???
    def unapply(tree: Import)(implicit ctx: Context): Option[(Term, List[ImportSelector])] = ???

    def extension_expr(self: Import)(implicit ctx: Context): Term = ???
    def extension_selectors(self: Import)(implicit ctx: Context): List[ImportSelector] = ???
  }
  implicit def given_TypeTest_Tree_Statement(implicit v: Context): TypeTest[Tree, Statement] = ???
  implicit def given_TypeTest_Tree_Definition(implicit v: Context): TypeTest[Tree, Definition] = ???
  object Definition {
    def extension_name(self: Definition)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_Tree_ClassDef(implicit v: Context): TypeTest[Tree, ClassDef] = ???
  object ClassDef {
    def copy(original: Tree)(name: String, constr: DefDef, parents: List[Tree ], derived: List[TypeTree], selfOpt: Option[ValDef], body: List[Statement])(implicit ctx: Context): ClassDef = ???
    def unapply(cdef: ClassDef)(implicit ctx: Context): Option[(String, DefDef, List[Tree ], List[TypeTree], Option[ValDef], List[Statement])] = ???

    def extension_constructor(self: ClassDef)(implicit ctx: Context): DefDef = ???
    def extension_parents(self: ClassDef)(implicit ctx: Context): List[Tree ] = ???
    def extension_derived(self: ClassDef)(implicit ctx: Context): List[TypeTree] = ???
    def extension_self(self: ClassDef)(implicit ctx: Context): Option[ValDef] = ???
    def extension_body(self: ClassDef)(implicit ctx: Context): List[Statement] = ???
  }
  implicit def given_TypeTest_Tree_DefDef(implicit v: Context): TypeTest[Tree, DefDef] = ???
  object DefDef {
    def apply(symbol: Symbol, rhsFn: List[Type] => List[List[Term]] => Option[Term])(implicit ctx: Context): DefDef = ???
    def copy(original: Tree)(name: String, typeParams: List[TypeDef], paramss: List[List[ValDef]], tpt: TypeTree, rhs: Option[Term])(implicit ctx: Context): DefDef = ???
    def unapply(ddef: DefDef)(implicit ctx: Context): Option[(String, List[TypeDef], List[List[ValDef]], TypeTree, Option[Term])] = ???

    def extension_typeParams(self: DefDef)(implicit ctx: Context): List[TypeDef] = ???
    def extension_paramss(self: DefDef)(implicit ctx: Context): List[List[ValDef]] = ???
    def extension_returnTpt(self: DefDef)(implicit ctx: Context): TypeTree = ???
    def extension_rhs(self: DefDef)(implicit ctx: Context): Option[Term] = ???
  }
  implicit def given_TypeTest_Tree_ValDef(implicit v: Context): TypeTest[Tree, ValDef] = ???
  object ValDef {
    def apply(symbol: Symbol, rhs: Option[Term])(implicit ctx: Context): ValDef = ???
    def copy(original: Tree)(name: String, tpt: TypeTree, rhs: Option[Term])(implicit ctx: Context): ValDef = ???
    def unapply(vdef: ValDef)(implicit ctx: Context): Option[(String, TypeTree, Option[Term])] = ???

    def extension_tpt(self: ValDef)(implicit ctx: Context): TypeTree = ???
    def extension_rhs(self: ValDef)(implicit ctx: Context): Option[Term] = ???
  }
  implicit def given_TypeTest_Tree_TypeDef(implicit v: Context): TypeTest[Tree, TypeDef] = ???
  object TypeDef {
    def apply(symbol: Symbol)(implicit ctx: Context): TypeDef = ???
    def copy(original: Tree)(name: String, rhs: Tree )(implicit ctx: Context): TypeDef = ???
    def unapply(tdef: TypeDef)(implicit ctx: Context): Option[(String, Tree  )] = ???

    def extension_rhs(self: TypeDef)(implicit ctx: Context): Tree  = ???
  }
  implicit def given_TypeTest_Tree_PackageDef(implicit v: Context): TypeTest[Tree, PackageDef] = ???
  object PackageDef {
    def unapply(tree: PackageDef)(implicit ctx: Context): Option[(String, PackageDef)] = ???

    def extension_owner(self: PackageDef)(implicit ctx: Context): PackageDef = ???
    def extension_members(self: PackageDef)(implicit ctx: Context): List[Statement] = ???
  }
  object Term {
    def extension_seal(self: Term)(implicit ctx: Context): scala.quoted.Expr[Any] = ???
    def extension_sealOpt(self: Term)(implicit ctx: Context): Option[scala.quoted.Expr[Any]] = ???
    def extension_tpe(self: Term)(implicit ctx: Context): Type = ???
    def extension_underlyingArgument(self: Term)(implicit ctx: Context): Term = ???
    def extension_underlying(self: Term)(implicit ctx: Context): Term = ???
    def extension_etaExpand(self: Term)(implicit ctx: Context): Term = ???
    def extension_appliedTo(self: Term, arg: Term)(implicit ctx: Context): Term = ???
    def extension_appliedTo(self: Term, arg: Term, args: Term*)(implicit ctx: Context): Term = ???
    def extension_appliedToArgs(self: Term, args: List[Term])(implicit ctx: Context): Apply = ???
    def extension_appliedToArgss(self: Term, argss: List[List[Term]])(implicit ctx: Context): Term = ???
    def extension_appliedToNone(self: Term)(implicit ctx: Context): Apply = ???
    def extension_appliedToType(self: Term, targ: Type)(implicit ctx: Context): Term = ???
    def extension_appliedToTypes(self: Term, targs: List[Type])(implicit ctx: Context): Term = ???
    def extension_appliedToTypeTrees(self: Term)(targs: List[TypeTree])(implicit ctx: Context): Term = ???
    def extension_select(self: Term)(sym: Symbol)(implicit ctx: Context): Select = ???
  }
  implicit def given_TypeTest_Tree_Term(implicit v: Context): TypeTest[Tree, Term] = ???
  implicit def given_TypeTest_Tree_Ref(implicit v: Context): TypeTest[Tree, Ref] = ???
  object Ref {
    def apply(sym: Symbol)(implicit ctx: Context): Ref = ???
    def term(tp: TermRef)(implicit ctx: Context): Ref = ???
  }
  implicit def given_TypeTest_Tree_Ident(implicit v: Context): TypeTest[Tree, Ident] = ???
  object Ident {
    def apply(tmref: TermRef)(implicit ctx: Context): Term = ???
    def copy(original: Tree)(name: String)(implicit ctx: Context): Ident = ???
    def unapply(tree: Ident)(implicit ctx: Context): Option[String] = ???

    def extension_name(self: Ident)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_Tree_Select(implicit v: Context): TypeTest[Tree, Select] = ???
  object Select {
    def apply(qualifier: Term, symbol: Symbol)(implicit ctx: Context): Select = ???
    def unique(qualifier: Term, name: String)(implicit ctx: Context): Select = ???
    def overloaded(qualifier: Term, name: String, targs: List[Type], args: List[Term])(implicit ctx: Context): Apply = ???
    def copy(original: Tree)(qualifier: Term, name: String)(implicit ctx: Context): Select = ???
    def unapply(x: Select)(implicit ctx: Context): Option[(Term, String)] = ???

    def extension_qualifier(self: Select)(implicit ctx: Context): Term = ???
    def extension_name(self: Select)(implicit ctx: Context): String = ???
    def extension_signature(self: Select)(implicit ctx: Context): Option[Signature] = ???
  }
  implicit def given_TypeTest_Tree_Literal(implicit v: Context): TypeTest[Tree, Literal] = ???
  object Literal {
    def apply(constant: Constant)(implicit ctx: Context): Literal = ???
    def copy(original: Tree)(constant: Constant)(implicit ctx: Context): Literal = ???
    def unapply(x: Literal)(implicit ctx: Context): Option[Constant] = ???

    def extension_constant(self: Literal)(implicit ctx: Context): Constant = ???
  }
  implicit def given_TypeTest_Tree_This(implicit v: Context): TypeTest[Tree, This] = ???
  object This {
    def apply(cls: Symbol)(implicit ctx: Context): This = ???
    def copy(original: Tree)(qual: Option[Id])(implicit ctx: Context): This = ???
    def unapply(x: This)(implicit ctx: Context): Option[Option[Id]] = ???

    def extension_id(self: This)(implicit ctx: Context): Option[Id] = ???
  }
  implicit def given_TypeTest_Tree_New(implicit v: Context): TypeTest[Tree, New] = ???
  object New {
    def apply(tpt: TypeTree)(implicit ctx: Context): New = ???
    def copy(original: Tree)(tpt: TypeTree)(implicit ctx: Context): New = ???
    def unapply(x: New)(implicit ctx: Context): Option[TypeTree] = ???

    def extension_tpt(self: New)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_NamedArg(implicit v: Context): TypeTest[Tree, NamedArg] = ???
  object NamedArg {
    def apply(name: String, arg: Term)(implicit ctx: Context): NamedArg = ???
    def copy(original: Tree)(name: String, arg: Term)(implicit ctx: Context): NamedArg = ???
    def unapply(x: NamedArg)(implicit ctx: Context): Option[(String, Term)] = ???

    def extension_name(self: NamedArg)(implicit ctx: Context): String = ???
    def extension_value(self: NamedArg)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_Apply(implicit v: Context): TypeTest[Tree, Apply] = ???
  object Apply {
    def apply(fun: Term, args: List[Term])(implicit ctx: Context): Apply = ???
    def copy(original: Tree)(fun: Term, args: List[Term])(implicit ctx: Context): Apply = ???
    def unapply(x: Apply)(implicit ctx: Context): Option[(Term, List[Term])] = ???

    def extension_fun(self: Apply)(implicit ctx: Context): Term = ???
    def extension_args(self: Apply)(implicit ctx: Context): List[Term] = ???
  }
  implicit def given_TypeTest_Tree_TypeApply(implicit v: Context): TypeTest[Tree, TypeApply] = ???
  object TypeApply {
    def apply(fun: Term, args: List[TypeTree])(implicit ctx: Context): TypeApply = ???
    def copy(original: Tree)(fun: Term, args: List[TypeTree])(implicit ctx: Context): TypeApply = ???
    def unapply(x: TypeApply)(implicit ctx: Context): Option[(Term, List[TypeTree])] = ???

    def extension_fun(self: TypeApply)(implicit ctx: Context): Term = ???
    def extension_args(self: TypeApply)(implicit ctx: Context): List[TypeTree] = ???
  }
  implicit def given_TypeTest_Tree_Super(implicit v: Context): TypeTest[Tree, Super] = ???
  object Super {
    def apply(qual: Term, mix: Option[Id])(implicit ctx: Context): Super = ???
    def copy(original: Tree)(qual: Term, mix: Option[Id])(implicit ctx: Context): Super = ???
    def unapply(x: Super)(implicit ctx: Context): Option[(Term, Option[Id])] = ???

    def extension_qualifier(self: Super)(implicit ctx: Context): Term = ???
    def extension_id(self: Super)(implicit ctx: Context): Option[Id] = ???
  }
  implicit def given_TypeTest_Tree_Typed(implicit v: Context): TypeTest[Tree, Typed] = ???
  object Typed {
    def apply(expr: Term, tpt: TypeTree)(implicit ctx: Context): Typed = ???
    def copy(original: Tree)(expr: Term, tpt: TypeTree)(implicit ctx: Context): Typed = ???
    def unapply(x: Typed)(implicit ctx: Context): Option[(Term, TypeTree)] = ???

    def extension_expr(self: Typed)(implicit ctx: Context): Term = ???
    def extension_tpt(self: Typed)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_Assign(implicit v: Context): TypeTest[Tree, Assign] = ???
  object Assign {
    def apply(lhs: Term, rhs: Term)(implicit ctx: Context): Assign = ???
    def copy(original: Tree)(lhs: Term, rhs: Term)(implicit ctx: Context): Assign = ???
    def unapply(x: Assign)(implicit ctx: Context): Option[(Term, Term)] = ???

    def extension_lhs(self: Assign)(implicit ctx: Context): Term = ???
    def extension_rhs(self: Assign)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_Block(implicit v: Context): TypeTest[Tree, Block] = ???
  object Block {
    def apply(stats: List[Statement], expr: Term)(implicit ctx: Context): Block = ???
    def copy(original: Tree)(stats: List[Statement], expr: Term)(implicit ctx: Context): Block = ???
    def unapply(x: Block)(implicit ctx: Context): Option[(List[Statement], Term)] = ???

    def extension_statements(self: Block)(implicit ctx: Context): List[Statement] = ???
    def extension_expr(self: Block)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_Closure(implicit v: Context): TypeTest[Tree, Closure] = ???
  object Closure {
    def apply(meth: Term, tpt: Option[Type])(implicit ctx: Context): Closure = ???
    def copy(original: Tree)(meth: Tree, tpt: Option[Type])(implicit ctx: Context): Closure = ???
    def unapply(x: Closure)(implicit ctx: Context): Option[(Term, Option[Type])] = ???

    def extension_meth(self: Closure)(implicit ctx: Context): Term = ???
    def extension_tpeOpt(self: Closure)(implicit ctx: Context): Option[Type] = ???
  }
  object Lambda {
    def unapply(tree: Block)(implicit ctx: Context): Option[(List[ValDef], Term)] = ???
    def apply(tpe: MethodType, rhsFn: List[Tree] => Tree)(implicit ctx: Context): Block = ???
  }
  implicit def given_TypeTest_Tree_If(implicit v: Context): TypeTest[Tree, If] = ???
  object If {
    def apply(cond: Term, thenp: Term, elsep: Term)(implicit ctx: Context): If = ???
    def copy(original: Tree)(cond: Term, thenp: Term, elsep: Term)(implicit ctx: Context): If = ???
    def unapply(tree: If)(implicit ctx: Context): Option[(Term, Term, Term)] = ???

    def extension_cond(self: If)(implicit ctx: Context): Term = ???
    def extension_thenp(self: If)(implicit ctx: Context): Term = ???
    def extension_elsep(self: If)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_Match(implicit v: Context): TypeTest[Tree, Match] = ???
  object Match {
    def apply(selector: Term, cases: List[CaseDef])(implicit ctx: Context): Match = ???
    def copy(original: Tree)(selector: Term, cases: List[CaseDef])(implicit ctx: Context): Match = ???
    def unapply(x: Match)(implicit ctx: Context): Option[(Term, List[CaseDef])] = ???

    def extension_scrutinee(self: Match)(implicit ctx: Context): Term = ???
    def extension_cases(self: Match)(implicit ctx: Context): List[CaseDef] = ???
  }
  implicit def given_TypeTest_Tree_GivenMatch(implicit v: Context): TypeTest[Tree, GivenMatch] = ???
  object GivenMatch {
    def apply(cases: List[CaseDef])(implicit ctx: Context): GivenMatch = ???
    def copy(original: Tree)(cases: List[CaseDef])(implicit ctx: Context): GivenMatch = ???
    def unapply(x: GivenMatch)(implicit ctx: Context): Option[List[CaseDef]] = ???

    def extension_cases(self: GivenMatch)(implicit ctx: Context): List[CaseDef] = ???
  }
  implicit def given_TypeTest_Tree_Try(implicit v: Context): TypeTest[Tree, Try] = ???
  object Try {
    def apply(expr: Term, cases: List[CaseDef], finalizer: Option[Term])(implicit ctx: Context): Try = ???
    def copy(original: Tree)(expr: Term, cases: List[CaseDef], finalizer: Option[Term])(implicit ctx: Context): Try = ???
    def unapply(x: Try)(implicit ctx: Context): Option[(Term, List[CaseDef], Option[Term])] = ???

    def extension_body(self: Try)(implicit ctx: Context): Term = ???
    def extension_cases(self: Try)(implicit ctx: Context): List[CaseDef] = ???
    def extension_finalizer(self: Try)(implicit ctx: Context): Option[Term] = ???
  }
  implicit def given_TypeTest_Tree_Return(implicit v: Context): TypeTest[Tree, Return] = ???
  object Return {
    def apply(expr: Term)(implicit ctx: Context): Return = ???
    def copy(original: Tree)(expr: Term)(implicit ctx: Context): Return = ???
    def unapply(x: Return)(implicit ctx: Context): Option[Term] = ???

    def extension_expr(self: Return)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_Repeated(implicit v: Context): TypeTest[Tree, Repeated] = ???
  object Repeated {
    def apply(elems: List[Term], tpt: TypeTree)(implicit ctx: Context): Repeated = ???
    def copy(original: Tree)(elems: List[Term], tpt: TypeTree)(implicit ctx: Context): Repeated = ???
    def unapply(x: Repeated)(implicit ctx: Context): Option[(List[Term], TypeTree)] = ???

    def extension_elems(self: Repeated)(implicit ctx: Context): List[Term] = ???
    def extension_elemtpt(self: Repeated)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_Inlined(implicit v: Context): TypeTest[Tree, Inlined] = ???
  object Inlined {
    def apply(call: Option[Tree ], bindings: List[Definition], expansion: Term)(implicit ctx: Context): Inlined = ???
    def copy(original: Tree)(call: Option[Tree ], bindings: List[Definition], expansion: Term)(implicit ctx: Context): Inlined = ???
    def unapply(x: Inlined)(implicit ctx: Context): Option[(Option[Tree ], List[Definition], Term)] = ???

    def extension_call(self: Inlined)(implicit ctx: Context): Option[Tree ] = ???
    def extension_bindings(self: Inlined)(implicit ctx: Context): List[Definition] = ???
    def extension_body(self: Inlined)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_SelectOuter(implicit v: Context): TypeTest[Tree, SelectOuter] = ???
  object SelectOuter {
    def apply(qualifier: Term, name: String, levels: Int)(implicit ctx: Context): SelectOuter = ???
    def copy(original: Tree)(qualifier: Term, name: String, levels: Int)(implicit ctx: Context): SelectOuter = ???
    def unapply(x: SelectOuter)(implicit ctx: Context): Option[(Term, Int, Type)] = ???

    def extension_qualifier(self: SelectOuter)(implicit ctx: Context): Term = ???
    def extension_level(self: SelectOuter)(implicit ctx: Context): Int = ???
  }
  implicit def given_TypeTest_Tree_While(implicit v: Context): TypeTest[Tree, While] = ???
  object While {
    def apply(cond: Term, body: Term)(implicit ctx: Context): While = ???
    def copy(original: Tree)(cond: Term, body: Term)(implicit ctx: Context): While = ???
    def unapply(x: While)(implicit ctx: Context): Option[(Term, Term)] = ???

    def extension_cond(self: While)(implicit ctx: Context): Term = ???
    def extension_body(self: While)(implicit ctx: Context): Term = ???
  }
  object TypeTree {
    def extension_tpe(self: TypeTree)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_Tree_TypeTree(implicit v: Context): TypeTest[Tree, TypeTree] = ???
  implicit def given_TypeTest_Tree_Inferred(implicit v: Context): TypeTest[Tree, Inferred] = ???
  object Inferred {
    def apply(tpe: Type)(implicit ctx: Context): Inferred = ???
    def unapply(x: Inferred)(implicit ctx: Context): Boolean = ???
  }
  implicit def given_TypeTest_Tree_TypeIdent(implicit v: Context): TypeTest[Tree, TypeIdent] = ???
  object TypeIdent {
    def apply(sym: Symbol)(implicit ctx: Context): TypeTree = ???
    def copy(original: Tree)(name: String)(implicit ctx: Context): TypeIdent = ???
    def unapply(x: TypeIdent)(implicit ctx: Context): Option[String] = ???

    def extension_name(self: TypeIdent)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_Tree_TypeSelect(implicit v: Context): TypeTest[Tree, TypeSelect] = ???
  object TypeSelect {
    def apply(qualifier: Term, name: String)(implicit ctx: Context): TypeSelect = ???
    def copy(original: Tree)(qualifier: Term, name: String)(implicit ctx: Context): TypeSelect = ???
    def unapply(x: TypeSelect)(implicit ctx: Context): Option[(Term, String)] = ???

    def extension_qualifier(self: TypeSelect)(implicit ctx: Context): Term = ???
    def extension_name(self: TypeSelect)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_Tree_Projection(implicit v: Context): TypeTest[Tree, Projection] = ???
  object Projection {
    def copy(original: Tree)(qualifier: TypeTree, name: String)(implicit ctx: Context): Projection = ???
    def unapply(x: Projection)(implicit ctx: Context): Option[(TypeTree, String)] = ???

    def extension_qualifier(self: Projection)(implicit ctx: Context): TypeTree = ???
    def extension_name(self: Projection)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_Tree_Singleton(implicit v: Context): TypeTest[Tree, Singleton] = ???
  object Singleton {
    def apply(ref: Term)(implicit ctx: Context): Singleton = ???
    def copy(original: Tree)(ref: Term)(implicit ctx: Context): Singleton = ???
    def unapply(x: Singleton)(implicit ctx: Context): Option[Term] = ???

    def extension_ref(self: Singleton)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_Refined(implicit v: Context): TypeTest[Tree, Refined] = ???
  object Refined {
    def copy(original: Tree)(tpt: TypeTree, refinements: List[Definition])(implicit ctx: Context): Refined = ???
    def unapply(x: Refined)(implicit ctx: Context): Option[(TypeTree, List[Definition])] = ???

    def extension_tpt(self: Refined)(implicit ctx: Context): TypeTree = ???
    def extension_refinements(self: Refined)(implicit ctx: Context): List[Definition] = ???
  }
  implicit def given_TypeTest_Tree_Applied(implicit v: Context): TypeTest[Tree, Applied] = ???
  object Applied {
    def apply(tpt: TypeTree, args: List[Tree ])(implicit ctx: Context): Applied = ???
    def copy(original: Tree)(tpt: TypeTree, args: List[Tree ])(implicit ctx: Context): Applied = ???
    def unapply(x: Applied)(implicit ctx: Context): Option[(TypeTree, List[Tree ])] = ???

    def extension_tpt(self: Applied)(implicit ctx: Context): TypeTree = ???
    def extension_args(self: Applied)(implicit ctx: Context): List[Tree ] = ???
  }
  implicit def given_TypeTest_Tree_Annotated(implicit v: Context): TypeTest[Tree, Annotated] = ???
  object Annotated {
    def apply(arg: TypeTree, annotation: Term)(implicit ctx: Context): Annotated = ???
    def copy(original: Tree)(arg: TypeTree, annotation: Term)(implicit ctx: Context): Annotated = ???
    def unapply(x: Annotated)(implicit ctx: Context): Option[(TypeTree, Term)] = ???

    def extension_arg(self: Annotated)(implicit ctx: Context): TypeTree = ???
    def extension_annotation(self: Annotated)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_MatchTypeTree(implicit v: Context): TypeTest[Tree, MatchTypeTree] = ???
  object MatchTypeTree {
    def apply(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef])(implicit ctx: Context): MatchTypeTree = ???
    def copy(original: Tree)(bound: Option[TypeTree], selector: TypeTree, cases: List[TypeCaseDef])(implicit ctx: Context): MatchTypeTree = ???
    def unapply(x: MatchTypeTree)(implicit ctx: Context): Option[(Option[TypeTree], TypeTree, List[TypeCaseDef])] = ???

    def extension_bound(self: MatchTypeTree)(implicit ctx: Context): Option[TypeTree] = ???
    def extension_selector(self: MatchTypeTree)(implicit ctx: Context): TypeTree = ???
    def extension_cases(self: MatchTypeTree)(implicit ctx: Context): List[TypeCaseDef] = ???
  }
  implicit def given_TypeTest_Tree_ByName(implicit v: Context): TypeTest[Tree, ByName] = ???
  object ByName {
    def apply(result: TypeTree)(implicit ctx: Context): ByName = ???
    def copy(original: Tree)(result: TypeTree)(implicit ctx: Context): ByName = ???
    def unapply(x: ByName)(implicit ctx: Context): Option[TypeTree] = ???

    def extension_result(self: ByName)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_LambdaTypeTree(implicit v: Context): TypeTest[Tree, LambdaTypeTree] = ???
  object LambdaTypeTree {
    def apply(tparams: List[TypeDef], body: Tree )(implicit ctx: Context): LambdaTypeTree = ???
    def copy(original: Tree)(tparams: List[TypeDef], body: Tree )(implicit ctx: Context): LambdaTypeTree = ???
    def unapply(tree: LambdaTypeTree)(implicit ctx: Context): Option[(List[TypeDef], Tree )] = ???

    def extension_tparams(self: LambdaTypeTree)(implicit ctx: Context): List[TypeDef] = ???
    def extension_body(self: LambdaTypeTree)(implicit ctx: Context): Tree  = ???
  }
  implicit def given_TypeTest_Tree_TypeBind(implicit v: Context): TypeTest[Tree, TypeBind] = ???
  object TypeBind {
    def copy(original: Tree)(name: String, tpt: Tree )(implicit ctx: Context): TypeBind = ???
    def unapply(x: TypeBind)(implicit ctx: Context): Option[(String, Tree )] = ???

    def extension_name(self: TypeBind)(implicit ctx: Context): String = ???
    def extension_body(self: TypeBind)(implicit ctx: Context): Tree  = ???
  }
  implicit def given_TypeTest_Tree_TypeBlock(implicit v: Context): TypeTest[Tree, TypeBlock] = ???
  object TypeBlock {
    def apply(aliases: List[TypeDef], tpt: TypeTree)(implicit ctx: Context): TypeBlock = ???
    def copy(original: Tree)(aliases: List[TypeDef], tpt: TypeTree)(implicit ctx: Context): TypeBlock = ???
    def unapply(x: TypeBlock)(implicit ctx: Context): Option[(List[TypeDef], TypeTree)] = ???

    def extension_aliases(self: TypeBlock)(implicit ctx: Context): List[TypeDef] = ???
    def extension_tpt(self: TypeBlock)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_TypeBoundsTree(implicit v: Context): TypeTest[Tree, TypeBoundsTree] = ???
  object TypeBoundsTree {
    def unapply(x: TypeBoundsTree)(implicit ctx: Context): Option[(TypeTree, TypeTree)] = ???

    def extension_tpe(self: TypeBoundsTree)(implicit ctx: Context): TypeBounds = ???
    def extension_low(self: TypeBoundsTree)(implicit ctx: Context): TypeTree = ???
    def extension_hi(self: TypeBoundsTree)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_WildcardTypeTree(implicit v: Context): TypeTest[Tree, WildcardTypeTree] = ???
  object WildcardTypeTree {
    def unapply(x: WildcardTypeTree)(implicit ctx: Context): Boolean = ???

    def extension_tpe(self: WildcardTypeTree)(implicit ctx: Context): TypeOrBounds = ???
  }
  implicit def given_TypeTest_Tree_CaseDef(implicit v: Context): TypeTest[Tree, CaseDef] = ???
  object CaseDef {
    def apply(pattern: Tree, guard: Option[Term], rhs: Term)(implicit ctx: Context): CaseDef = ???
    def copy(original: Tree)(pattern: Tree, guard: Option[Term], rhs: Term)(implicit ctx: Context): CaseDef = ???
    def unapply(x: CaseDef)(implicit ctx: Context): Option[(Tree, Option[Term], Term)] = ???

    def extension_pattern(caseDef: CaseDef)(implicit ctx: Context): Tree = ???
    def extension_guard(caseDef: CaseDef)(implicit ctx: Context): Option[Term] = ???
    def extension_rhs(caseDef: CaseDef)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_Tree_TypeCaseDef(implicit v: Context): TypeTest[Tree, TypeCaseDef] = ???
  object TypeCaseDef {
    def apply(pattern: TypeTree, rhs: TypeTree)(implicit ctx: Context): TypeCaseDef = ???
    def copy(original: Tree)(pattern: TypeTree, rhs: TypeTree)(implicit ctx: Context): TypeCaseDef = ???
    def unapply(tree: TypeCaseDef)(implicit ctx: Context): Option[(TypeTree, TypeTree)] = ???

    def extension_pattern(caseDef: TypeCaseDef)(implicit ctx: Context): TypeTree = ???
    def extension_rhs(caseDef: TypeCaseDef)(implicit ctx: Context): TypeTree = ???
  }
  implicit def given_TypeTest_Tree_Bind(implicit v: Context): TypeTest[Tree, Bind] = ???
  object Bind {
    def copy(original: Tree)(name: String, pattern: Tree)(implicit ctx: Context): Bind = ???
    def unapply(pattern: Bind)(implicit ctx: Context): Option[(String, Tree)] = ???

    def extension_name(bind: Bind)(implicit ctx: Context): String = ???
    def extension_pattern(bind: Bind)(implicit ctx: Context): Tree = ???
  }
  implicit def given_TypeTest_Tree_Unapply(implicit v: Context): TypeTest[Tree, Unapply] = ???
  object Unapply {
    def copy(original: Tree)(fun: Term, implicits: List[Term], patterns: List[Tree])(implicit ctx: Context): Unapply = ???
    def unapply(x: Unapply)(implicit ctx: Context): Option[(Term, List[Term], List[Tree])] = ???

    def extension_fun(unapply: Unapply)(implicit ctx: Context): Term = ???
    def extension_implicits(unapply: Unapply)(implicit ctx: Context): List[Term] = ???
    def extension_patterns(unapply: Unapply)(implicit ctx: Context): List[Tree] = ???
  }
  implicit def given_TypeTest_Tree_Alternatives(implicit v: Context): TypeTest[Tree, Alternatives] = ???
  object Alternatives {
    def apply(patterns: List[Tree])(implicit ctx: Context): Alternatives = ???
    def copy(original: Tree)(patterns: List[Tree])(implicit ctx: Context): Alternatives = ???
    def unapply(x: Alternatives)(implicit ctx: Context): Option[List[Tree]] = ???

    def extension_patterns(alternatives: Alternatives)(implicit ctx: Context): List[Tree] = ???
  }

  // Import selectors

  implicit def given_TypeTest_ImportSelector_SimpleSelector(implicit v: Context): TypeTest[ImportSelector, SimpleSelector] = ???
  object SimpleSelector {
    def unapply(x: SimpleSelector)(implicit ctx: Context): Option[Id] = ???

    def extension_selection(self: SimpleSelector)(implicit ctx: Context): Id = ???
  }
  implicit def given_TypeTest_ImportSelector_RenameSelector(implicit v: Context): TypeTest[ImportSelector, RenameSelector] = ???
  object RenameSelector {
    def unapply(x: RenameSelector)(implicit ctx: Context): Option[(Id, Id)] = ???

    def extension_from(self: RenameSelector)(implicit ctx: Context): Id = ???
    def extension_to(self: RenameSelector)(implicit ctx: Context): Id = ???
  }
  implicit def given_TypeTest_ImportSelector_OmitSelector(implicit v: Context): TypeTest[ImportSelector, OmitSelector] = ???
  object OmitSelector {
    def unapply(x: OmitSelector)(implicit ctx: Context): Option[Id] = ???

    def extension_omitted(self: OmitSelector)(implicit ctx: Context): Id = ???
  }

  // Types

  def typeOf[T](implicit qtype: scala.quoted.Type[T], ctx: Context): Type = ???

  implicit class TypeOrBoundsOps(tpe: TypeOrBounds) {
    def showExtractors(implicit ctx: Context): String = ???
    def show(implicit ctx: Context): String = ???
    def show(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }

  implicit def given_TypeTest_TypeOrBounds_Type(implicit v: Context): TypeTest[TypeOrBounds, Type] = ???
  object Type {
    def apply(clazz: Class[_])(implicit ctx: Context): Type = ???

    def extension_seal(self: Type)(implicit ctx: Context): scala.quoted.Type[_] = ???
    def extension_=:=(self: Type)(that: Type)(implicit ctx: Context): Boolean = ???
    def extension_<:<(self: Type)(that: Type)(implicit ctx: Context): Boolean = ???
    def extension_widen(self: Type)(implicit ctx: Context): Type = ???
    def extension_widenTermRefExpr(self: Type)(implicit ctx: Context): Type = ???
    def extension_dealias(self: Type)(implicit ctx: Context): Type = ???
    def extension_simplified(self: Type)(implicit ctx: Context): Type = ???
    def extension_classSymbol(self: Type)(implicit ctx: Context): Option[Symbol] = ???
    def extension_typeSymbol(self: Type)(implicit ctx: Context): Symbol = ???
    def extension_termSymbol(self: Type)(implicit ctx: Context): Symbol = ???
    def extension_isSingleton(self: Type)(implicit ctx: Context): Boolean = ???
    def extension_memberType(self: Type)(member: Symbol)(implicit ctx: Context): Type = ???
    def extension_baseClasses(self: Type)(implicit ctx: Context): List[Symbol] = ???
    def extension_baseType(self: Type)(member: Symbol)(implicit ctx: Context): Type = ???
    def extension_derivesFrom(self: Type)(cls: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def extension_isContextFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def extension_isErasedFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def extension_isDependentFunctionType(self: Type)(implicit ctx: Context): Boolean = ???
    def extension_select(self: Type)(sym: Symbol)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_ConstantType(implicit v: Context): TypeTest[TypeOrBounds, ConstantType] = ???
  object ConstantType {
    def apply(x : Constant)(implicit ctx: Context): ConstantType = ???
    def unapply(x: ConstantType)(implicit ctx: Context): Option[Constant] = ???

    def extension_constant(self: ConstantType)(implicit ctx: Context): Constant = ???
  }
  implicit def given_TypeTest_TypeOrBounds_TermRef(implicit v: Context): TypeTest[TypeOrBounds, TermRef] = ???
  object TermRef {
    def apply(qual: TypeOrBounds, name: String)(implicit ctx: Context): TermRef = ???
    def unapply(x: TermRef)(implicit ctx: Context): Option[(TypeOrBounds , String)] = ???

    def extension_qualifier(self: TermRef)(implicit ctx: Context): TypeOrBounds  = ???
    def extension_name(self: TermRef)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_TypeOrBounds_TypeRef(implicit v: Context): TypeTest[TypeOrBounds, TypeRef] = ???
  object TypeRef {
    def unapply(x: TypeRef)(implicit ctx: Context): Option[(TypeOrBounds , String)] = ???

    def extension_qualifier(self: TypeRef)(implicit ctx: Context): TypeOrBounds  = ???
    def extension_name(self: TypeRef)(implicit ctx: Context): String = ???
    def extension_isOpaqueAlias(self: TypeRef)(implicit  ctx: Context): Boolean = ???
    def extension_translucentSuperType(self: TypeRef)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_SuperType(implicit v: Context): TypeTest[TypeOrBounds, SuperType] = ???
  object SuperType {
    def apply(thistpe: Type, supertpe: Type)(implicit ctx: Context): SuperType = ???
    def unapply(x: SuperType)(implicit ctx: Context): Option[(Type, Type)] = ???

    def extension_thistpe(self: SuperType)(implicit ctx: Context): Type = ???
    def extension_supertpe(self: SuperType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_Refinement(implicit v: Context): TypeTest[TypeOrBounds, Refinement] = ???
  object Refinement {
    def apply(parent: Type, name: String, info: TypeOrBounds )(implicit ctx: Context): Refinement = ???
    def unapply(x: Refinement)(implicit ctx: Context): Option[(Type, String, TypeOrBounds )] = ???

    def extension_parent(self: Refinement)(implicit ctx: Context): Type = ???
    def extension_name(self: Refinement)(implicit ctx: Context): String = ???
    def extension_info(self: Refinement)(implicit ctx: Context): TypeOrBounds = ???
  }
  implicit def given_TypeTest_TypeOrBounds_AppliedType(implicit v: Context): TypeTest[TypeOrBounds, AppliedType] = ???
  object AppliedType {
    def apply(tycon: Type, args: List[TypeOrBounds])(implicit ctx: Context): AppliedType = ???
    def unapply(x: AppliedType)(implicit ctx: Context): Option[(Type, List[TypeOrBounds ])] = ???

    def extension_tycon(self: AppliedType)(implicit ctx: Context): Type = ???
    def extension_args(self: AppliedType)(implicit ctx: Context): List[TypeOrBounds ] = ???
  }
  implicit def given_TypeTest_TypeOrBounds_AnnotatedType(implicit v: Context): TypeTest[TypeOrBounds, AnnotatedType] = ???
  object AnnotatedType {
    def apply(underlying: Type, annot: Term)(implicit ctx: Context): AnnotatedType = ???
    def unapply(x: AnnotatedType)(implicit ctx: Context): Option[(Type, Term)] = ???

    def extension_underlying(self: AnnotatedType)(implicit ctx: Context): Type = ???
    def extension_annot(self: AnnotatedType)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_TypeOrBounds_AndType(implicit v: Context): TypeTest[TypeOrBounds, AndType] = ???
  object AndType {
    def apply(lhs: Type, rhs: Type)(implicit ctx: Context): AndType = ???
    def unapply(x: AndType)(implicit ctx: Context): Option[(Type, Type)] = ???

    def extension_left(self: AndType)(implicit ctx: Context): Type = ???
    def extension_right(self: AndType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_OrType(implicit v: Context): TypeTest[TypeOrBounds, OrType] = ???
  object OrType {
    def apply(lhs: Type, rhs: Type)(implicit ctx: Context): OrType = ???
    def unapply(x: OrType)(implicit ctx: Context): Option[(Type, Type)] = ???

    def extension_left(self: OrType)(implicit ctx: Context): Type = ???
    def extension_right(self: OrType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_MatchType(implicit v: Context): TypeTest[TypeOrBounds, MatchType] = ???
  object MatchType {
    def apply(bound: Type, scrutinee: Type, cases: List[Type])(implicit ctx: Context): MatchType = ???
    def unapply(x: MatchType)(implicit ctx: Context): Option[(Type, Type, List[Type])] = ???

    def extension_bound(self: MatchType)(implicit ctx: Context): Type = ???
    def extension_scrutinee(self: MatchType)(implicit ctx: Context): Type = ???
    def extension_cases(self: MatchType)(implicit ctx: Context): List[Type] = ???
  }
  def MatchCaseType(implicit ctx: Context): Type = ???
  implicit def given_TypeTest_TypeOrBounds_ByNameType(implicit v: Context): TypeTest[TypeOrBounds, ByNameType] = ???
  object ByNameType {
    def apply(underlying: Type)(implicit ctx: Context): Type = ???
    def unapply(x: ByNameType)(implicit ctx: Context): Option[Type] = ???

    def extension_underlying(self: ByNameType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_ParamRef(implicit v: Context): TypeTest[TypeOrBounds, ParamRef] = ???
  object ParamRef {
    def unapply(x: ParamRef)(implicit ctx: Context): Option[(LambdaType[TypeOrBounds], Int)] = ???

    def extension_binder(self: ParamRef)(implicit ctx: Context): LambdaType[TypeOrBounds] = ???
    def extension_paramNum(self: ParamRef)(implicit ctx: Context): Int = ???
  }
  implicit def given_TypeTest_TypeOrBounds_ThisType(implicit v: Context): TypeTest[TypeOrBounds, ThisType] = ???
  object ThisType {
    def unapply(x: ThisType)(implicit ctx: Context): Option[Type] = ???

    def extension_tref(self: ThisType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_RecursiveThis(implicit v: Context): TypeTest[TypeOrBounds, RecursiveThis] = ???
  object RecursiveThis {
    def unapply(x: RecursiveThis)(implicit ctx: Context): Option[RecursiveType] = ???

    def extension_binder(self: RecursiveThis)(implicit ctx: Context): RecursiveType = ???
  }
  implicit def given_TypeTest_TypeOrBounds_RecursiveType(implicit v: Context): TypeTest[TypeOrBounds, RecursiveType] = ???
  object RecursiveType {
    def apply(parentExp: RecursiveType => Type)(implicit ctx: Context): RecursiveType = ???
    def unapply(x: RecursiveType)(implicit ctx: Context): Option[Type] = ???

    def extension_underlying(self: RecursiveType)(implicit ctx: Context): Type = ???
    def extension_recThis(self: RecursiveType)(implicit ctx: Context): RecursiveThis = ???
  }
  implicit def given_TypeTest_TypeOrBounds_MethodType(implicit v: Context): TypeTest[TypeOrBounds, MethodType] = ???
  object MethodType {
    def apply(paramNames: List[String])(paramInfosExp: MethodType => List[Type], resultTypeExp: MethodType => Type): MethodType = ???
    def unapply(x: MethodType)(implicit ctx: Context): Option[(List[String], List[Type], Type)] = ???

    def extension_isImplicit(self: MethodType): Boolean = ???
    def extension_isErased(self: MethodType): Boolean = ???
    def extension_param(self: MethodType)(idx: Int)(implicit ctx: Context): Type = ???
    def extension_paramNames(self: MethodType)(implicit ctx: Context): List[String] = ???
    def extension_paramTypes(self: MethodType)(implicit ctx: Context): List[Type] = ???
    def extension_resType(self: MethodType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_PolyType(implicit v: Context): TypeTest[TypeOrBounds, PolyType] = ???
  object PolyType {
    def apply(paramNames: List[String])(paramBoundsExp: PolyType => List[TypeBounds], resultTypeExp: PolyType => Type)(implicit ctx: Context): PolyType = ???
    def unapply(x: PolyType)(implicit ctx: Context): Option[(List[String], List[TypeBounds], Type)] = ???

    def extension_param(self: PolyType)(idx: Int)(implicit ctx: Context): Type = ???
    def extension_paramNames(self: PolyType)(implicit ctx: Context): List[String] = ???
    def extension_paramBounds(self: PolyType)(implicit ctx: Context): List[TypeBounds] = ???
    def extension_resType(self: PolyType)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_TypeLambda(implicit v: Context): TypeTest[TypeOrBounds, TypeLambda] = ???
  object TypeLambda {
    def apply(paramNames: List[String], boundsFn: TypeLambda => List[TypeBounds], bodyFn: TypeLambda => Type): TypeLambda = ???
    def unapply(x: TypeLambda)(implicit ctx: Context): Option[(List[String], List[TypeBounds], Type)] = ???

    def extension_paramNames(self: TypeLambda)(implicit ctx: Context): List[String] = ???
    def extension_paramBounds(self: TypeLambda)(implicit ctx: Context): List[TypeBounds] = ???
    def extension_param(self: TypeLambda)(idx: Int)(implicit ctx: Context) : Type = ???
    def extension_resType(self: TypeLambda)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_TypeBounds(implicit v: Context): TypeTest[TypeOrBounds, TypeBounds] = ???
  object TypeBounds {
    def apply(low: Type, hi: Type)(implicit ctx: Context): TypeBounds = ???
    def unapply(x: TypeBounds)(implicit ctx: Context): Option[(Type, Type)] = ???

    def extension_low(self: TypeBounds)(implicit ctx: Context): Type = ???
    def extension_hi(self: TypeBounds)(implicit ctx: Context): Type = ???
  }
  implicit def given_TypeTest_TypeOrBounds_NoPrefix(implicit v: Context): TypeTest[TypeOrBounds, NoPrefix] = ???
  object NoPrefix {
    def unapply(x: NoPrefix)(implicit ctx: Context): Boolean = ???
  }

  // Constants

  object Constant {
    def apply(x: Any): Constant = ???
    def unapply(constant: Constant): Option[Any] = ???
    object ClassTag {
      def apply[T](implicit x: Type): Constant = ???
      def unapply(constant: Constant): Option[Type] = ???
    }

    def extension_value(const: Constant): Any = ???
    def extension_showExtractors(const: Constant)(implicit ctx: Context): String = ???
    def extension_show(const: Constant)(implicit ctx: Context): String = ???
    def extension_showWith(const: Constant, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }

  // IDs

  object Id {
    def unapply(id: Id)(implicit ctx: Context): Option[String] = ???

    def extension_pos(id: Id)(implicit ctx: Context): Position = ???
    def extension_name(id: Id)(implicit ctx: Context): String = ???
  }

  // Implicits

  def searchImplicit(tpe: Type)(implicit ctx: Context): ImplicitSearchResult = ???
  implicit def given_TypeTest_ImplicitSearchResult_ImplicitSearchSuccess(implicit v: Context): TypeTest[ImplicitSearchResult, ImplicitSearchSuccess] = ???
  object ImplicitSearchSuccess {
    def extension_tree(self: ImplicitSearchSuccess)(implicit ctx: Context): Term = ???
  }
  implicit def given_TypeTest_ImplicitSearchResult_ImplicitSearchFailure(implicit v: Context): TypeTest[ImplicitSearchResult, ImplicitSearchFailure] = ???
  object ImplicitSearchFailure {
    def extension_explanation(self: ImplicitSearchFailure)(implicit ctx: Context): String = ???
  }
  implicit def given_TypeTest_ImplicitSearchResult_DivergingImplicit(implicit v: Context): TypeTest[ImplicitSearchResult, DivergingImplicit] = ???
  implicit def given_TypeTest_ImplicitSearchResult_NoMatchingImplicits(implicit v: Context): TypeTest[ImplicitSearchResult, NoMatchingImplicits] = ???
  implicit def given_TypeTest_ImplicitSearchResult_AmbiguousImplicits(implicit v: Context): TypeTest[ImplicitSearchResult, AmbiguousImplicits] = ???

  // Symbol

  object Symbol {
    def currentOwner(implicit ctx: Context): Symbol = ???
    def requiredPackage(path: String)(implicit ctx: Context): Symbol = ???
    def requiredClass(path: String)(implicit ctx: Context): Symbol = ???
    def requiredModule(path: String)(implicit ctx: Context): Symbol = ???
    def requiredMethod(path: String)(implicit ctx: Context): Symbol = ???
    def classSymbol(fullName: String)(implicit ctx: Context): Symbol = ???
    def newMethod(parent: Symbol, name: String, tpe: Type)(implicit ctx: Context): Symbol = ???
    def newMethod(parent: Symbol, name: String, tpe: Type, flags: Flags, privateWithin: Symbol)(implicit ctx: Context): Symbol = ???
    def newVal(parent: Symbol, name: String, tpe: Type, flags: Flags, privateWithin: Symbol)(implicit ctx: Context): Symbol = ???
    def newBind(parent: Symbol, name: String, flags: Flags, tpe: Type)(implicit ctx: Context): Symbol = ???
    def noSymbol(implicit ctx: Context): Symbol = ???

    def extension_owner(self: Symbol)(implicit ctx: Context): Symbol = ???
    def extension_maybeOwner(self: Symbol)(implicit ctx: Context): Symbol = ???
    def extension_flags(self: Symbol)(implicit ctx: Context): Flags = ???
    def extension_privateWithin(self: Symbol)(implicit ctx: Context): Option[Type] = ???
    def extension_protectedWithin(self: Symbol)(implicit ctx: Context): Option[Type] = ???
    def extension_name(self: Symbol)(implicit ctx: Context): String = ???
    def extension_fullName(self: Symbol)(implicit ctx: Context): String = ???
    def extension_pos(self: Symbol)(implicit ctx: Context): Position = ???
    def extension_localContext(self: Symbol)(implicit ctx: Context): Context = ???
    def extension_comment(self: Symbol)(implicit ctx: Context): Option[Comment] = ???
    def extension_tree(self: Symbol)(implicit ctx: Context): Tree = ???
    def extension_annots(self: Symbol)(implicit ctx: Context): List[Term] = ???
    def extension_isDefinedInCurrentRun(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isLocalDummy(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isRefinementClass(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isAliasType(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isAnonymousClass(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isAnonymousFunction(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isAbstractType(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isClassConstructor(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isType(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isTerm(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isPackageDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isClassDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isTypeDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isValDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isDefDef(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isBind(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_isNoSymbol(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_exists(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_fields(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def extension_field(self: Symbol)(name: String)(implicit ctx: Context): Symbol = ???
    def extension_classMethod(self: Symbol)(name: String)(implicit ctx: Context): List[Symbol] = ???
    def extension_classMethods(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def extension_method(self: Symbol)(name: String)(implicit ctx: Context): List[Symbol] = ???
    def extension_methods(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def extension_caseFields(self: Symbol)(implicit ctx: Context): List[Symbol] = ???
    def extension_isTypeParam(self: Symbol)(implicit ctx: Context): Boolean = ???
    def extension_signature(self: Symbol)(implicit ctx: Context): Signature = ???
    def extension_moduleClass(self: Symbol)(implicit ctx: Context): Symbol = ???
    def extension_companionClass(self: Symbol)(implicit ctx: Context): Symbol = ???
    def extension_companionModule(self: Symbol)(implicit ctx: Context): Symbol = ???
    def extension_showExtractors(symbol: Symbol)(implicit ctx: Context): String = ???
    def extension_show(symbol: Symbol)(implicit ctx: Context): String = ???
    def extension_showWith(symbol: Symbol, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
    def extension_children(symbol: Symbol)(implicit ctx: Context): List[Symbol] = ???
  }

  // Signature

  object Signature {
    def unapply(sig: Signature)(implicit ctx: Context): Option[(List[Any], String)] = ???

    def extension_paramSigs(sig: Signature): List[Any] = ???
    def extension_resultSig(sig: Signature): String = ???
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
    def StringType: Type = ???
    def TypleType: Type = ???
    def EmptyTypleType: Type = ???
    def NonEmptyTypleType: Type = ???
    def TypleConsType: Type = ???
  }

  // Flags

  object Flags {
    def Abstract: Flags = ???
    def Artifact: Flags = ???
    def Case: Flags = ???
    def CaseAcessor: Flags = ???
    def Contravariant: Flags = ???
    def Covariant: Flags = ???
    def EmptyFlags: Flags = ???
    def Enum: Flags = ???
    def Erased: Flags = ???
    def ExtensionMethod: Flags = ???
    def FiledAccessor: Flags = ???
    def Final: Flags = ???
    def Given: Flags = ???
    def HasDefault: Flags = ???
    def Implicit: Flags = ???
    def Inline: Flags = ???
    def JavaDefined: Flags = ???
    def Lazy: Flags = ???
    def Local: Flags = ???
    def Macro: Flags = ???
    def ModuleClass: Flags = ???
    def Mutable: Flags = ???
    def Object: Flags = ???
    def Override: Flags = ???
    def Package: Flags = ???
    def Param: Flags = ???
    def ParamAccessor: Flags = ???
    def Private: Flags = ???
    def PrivateLocal: Flags = ???
    def Protected: Flags = ???
    def Scala2X: Flags = ???
    def Sealed: Flags = ???
    def StableRealizable: Flags = ???
    def Static: Flags = ???
    def Synthetic: Flags = ???
    def Trait: Flags = ???
    def FieldAccessor: Flags = ???

    def extension_is(self: Flags)(that: Flags): Boolean = ???
    def extension_|(self: Flags)(that: Flags): Flags = ???
    def extension_&(self: Flags)(that: Flags): Flags = ???
    def extension_showExtractors(flags: Flags)(implicit ctx: Context): String = ???
    def extension_show(flags: Flags)(implicit ctx: Context): String = ???
    def extension_showWith(flags: Flags, syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = ???
  }

  // Positions

  def rootPosition: Position = ???

  object Position {
    def extension_start(pos: Position): Int = ???
    def extension_end(pos: Position): Int = ???
    def extension_exists(pos: Position): Boolean = ???
    def extension_sourceFile(pos: Position): SourceFile = ???
    def extension_startLine(pos: Position): Int = ???
    def extension_endLine(pos: Position): Int = ???
    def extension_startColumn(pos: Position): Int = ???
    def extension_endColumn(pos: Position): Int = ???
    def extension_sourceCode(pos: Position): String = ???
  }
  object SourceFile {
    def extension_jpath(sourceFile: SourceFile): java.nio.file.Path = ???
    def extension_content(sourceFile: SourceFile): String = ???
  }

  // Reporting

  def error(msg: => String, pos: Position)(implicit ctx: Context): Unit = ???
  def error(msg: => String, source: SourceFile, start: Int, end: Int)(implicit ctx: Context): Unit = ???
  def warning(msg: => String, pos: Position)(implicit ctx: Context): Unit = ???
  def warning(msg: => String, source: SourceFile, start: Int, end: Int)(implicit ctx: Context): Unit = ???

  // Comments

  object Comment {
    def extension_raw(self: Comment): String = ???
    def extension_expanded(self: Comment): Option[String] = ???
    def extension_usecases(self: Comment): List[(String, Option[DefDef])] = ???
  }

  // Utils

  trait TreeAccumulator[X] extends reflect.TreeAccumulator[X] {
    val reflect: reflectSelf.type = reflectSelf
  }
  trait TreeTraverser extends reflect.TreeTraverser {
    val reflect: reflectSelf.type = reflectSelf
  }
  trait TreeMap extends reflect.TreeMap {
    val reflect: reflectSelf.type = reflectSelf
  }
  def let(rhs: Term)(body: Ident => Term): Term = ???
  def lets(terms: List[Term])(body: List[Term] => Term): Term = ???

}
