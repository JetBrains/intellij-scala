package scala.tasty.compat

import scala.quoted.show.SyntaxHighlight

// Reproduces the API of https://github.com/lampepfl/dotty/blob/0.27.0-RC1/library/src/scala/tasty/Reflection.scala on top of the Scala 2.x ABI.
// Is required because Scala 2.x doesn't support extension methods (and implicit AnyVal classes in a class):
// https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html
class Reflection(val delegate: scala.tasty.Reflection) {
  import delegate._

  implicit class FlagsOps(self: Flags) {
    def is(that: Flags): Boolean = Flags.extension_is(self)(that)
    def |(that: Flags): Flags = Flags.extension_|(self)(that)
    def &(that: Flags): Flags = Flags.extension_&(self)(that)
    def showExtractors(implicit ctx: Context): String = Flags.extension_showExtractors(self)
    def show(implicit ctx: Context): String = Flags.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = Flags.extension_showWith(self, syntaxHighlight)
  }

  implicit class ConstantOps(self: Constant) {
    def value: Any = Constant.extension_value(self)
    def showExtractors(implicit ctx: Context): String = Constant.extension_showExtractors(self)
    def show(implicit ctx: Context): String = Constant.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = Constant.extension_showWith(self, syntaxHighlight)
  }

  implicit class IdOps(self: Id) {
    def pos(implicit ctx: Context): Position = Id.extension_pos(self)
    def name(implicit ctx: Context): String = Id.extension_name(self)
  }

  implicit class ImplicitSearchSuccessOps(self: ImplicitSearchSuccess) {
    def tree(implicit ctx: Context): Term = ImplicitSearchSuccess.extension_tree(self)
  }
  implicit class ImplicitSearchFailureOps(self: ImplicitSearchFailure) {
    def explanation(implicit ctx: Context): String = ImplicitSearchFailure.extension_explanation(self)
  }

  implicit class simpleSelectorOps(self: SimpleSelector) {
    def selection(implicit ctx: Context): Id = SimpleSelector.extension_selection(self)
  }
  implicit class renameSelectorOps(self: RenameSelector) {
    def from(implicit ctx: Context): Id = RenameSelector.extension_from(self)
    def to(implicit ctx: Context): Id = RenameSelector.extension_to(self)
  }
  implicit class omitSelectorOps(self: OmitSelector) {
    def omitted(implicit ctx: Context): Id = OmitSelector.extension_omitted(self)
  }

  implicit class PositionOps(self: Position) {
    def start: Int = Position.extension_start(self)
    def end: Int = Position.extension_end(self)
    def exists: Boolean = Position.extension_exists(self)
    def sourceFile: SourceFile = Position.extension_sourceFile(self)
    def startLine: Int = Position.extension_startLine(self)
    def endLine: Int = Position.extension_endLine(self)
    def startColumn: Int = Position.extension_startColumn(self)
    def endColumn: Int = Position.extension_endColumn(self)
    def sourceCode: String = Position.extension_sourceCode(self)
  }
  implicit class SourceFileOps(self: SourceFile) {
    def jpath: java.nio.file.Path = SourceFile.extension_jpath(self)
    def content: String = SourceFile.extension_content(self)
  }

  implicit class SignatureOps(self: Signature) {
    def paramSigs: List[Any] = Signature.extension_paramSigs(self)
    def resultSig: String = Signature.extension_resultSig(self)
  }

  implicit class SymbolOps(self: Symbol) {
    def owner(implicit ctx: Context): Symbol = Symbol.extension_owner(self)
    def maybeOwner(implicit ctx: Context): Symbol = Symbol.extension_maybeOwner(self)
    def flags(implicit ctx: Context): Flags = Symbol.extension_flags(self)
    def privateWithin(implicit ctx: Context): Option[Type] = Symbol.extension_privateWithin(self)
    def protectedWithin(implicit ctx: Context): Option[Type] = Symbol.extension_protectedWithin(self)
    def name(implicit ctx: Context): String = Symbol.extension_name(self)
    def fullName(implicit ctx: Context): String = Symbol.extension_fullName(self)
    def pos(implicit ctx: Context): Position = Symbol.extension_pos(self)
    def localContext(implicit ctx: Context): Context = Symbol.extension_localContext(self)
    def comment(implicit ctx: Context): Option[Comment] = Symbol.extension_comment(self)
    def tree(implicit ctx: Context): Tree = Symbol.extension_tree(self)
    def annots(implicit ctx: Context): List[Term] = Symbol.extension_annots(self)
    def isDefinedInCurrentRun(implicit ctx: Context): Boolean = Symbol.extension_isDefinedInCurrentRun(self)
    def isLocalDummy(implicit ctx: Context): Boolean = Symbol.extension_isLocalDummy(self)
    def isRefinementClass(implicit ctx: Context): Boolean = Symbol.extension_isRefinementClass(self)
    def isAliasType(implicit ctx: Context): Boolean = Symbol.extension_isAbstractType(self)
    def isAnonymousClass(implicit ctx: Context): Boolean = Symbol.extension_isAnonymousClass(self)
    def isAnonymousFunction(implicit ctx: Context): Boolean = Symbol.extension_isAnonymousFunction(self)
    def isAbstractType(implicit ctx: Context): Boolean = Symbol.extension_isAbstractType(self)
    def isClassConstructor(implicit ctx: Context): Boolean = Symbol.extension_isClassConstructor(self)
    def isType(implicit ctx: Context): Boolean = Symbol.extension_isType(self)
    def isTerm(implicit ctx: Context): Boolean = Symbol.extension_isTerm(self)
    def isPackageDef(implicit ctx: Context): Boolean = Symbol.extension_isPackageDef(self)
    def isClassDef(implicit ctx: Context): Boolean = Symbol.extension_isClassDef(self)
    def isTypeDef(implicit ctx: Context): Boolean = Symbol.extension_isTypeDef(self)
    def isValDef(implicit ctx: Context): Boolean = Symbol.extension_isValDef(self)
    def isDefDef(implicit ctx: Context): Boolean = Symbol.extension_isDefDef(self)
    def isBind(implicit ctx: Context): Boolean = Symbol.extension_isBind(self)
    def isNoSymbol(implicit ctx: Context): Boolean = Symbol.extension_isNoSymbol(self)
    def exists(implicit ctx: Context): Boolean = Symbol.extension_exists(self)
    def fields(implicit ctx: Context): List[Symbol] = Symbol.extension_fields(self)
    def field(name: String)(implicit ctx: Context): Symbol = Symbol.extension_field(self)(name)
    def classMethod(name: String)(implicit ctx: Context): List[Symbol] = Symbol.extension_classMethod(self)(name)
    def classMethods(implicit ctx: Context): List[Symbol] = Symbol.extension_classMethods(self)
    def method(name: String)(implicit ctx: Context): List[Symbol] = Symbol.extension_method(self)(name)
    def methods(implicit ctx: Context): List[Symbol] = Symbol.extension_methods(self)
    def caseFields(implicit ctx: Context): List[Symbol] = Symbol.extension_caseFields(self)
    def isTypeParam(implicit ctx: Context): Boolean = Symbol.extension_isTypeParam(self)
    def signature(implicit ctx: Context): Signature = Symbol.extension_signature(self)
    def moduleClass(implicit ctx: Context): Symbol = Symbol.extension_moduleClass(self)
    def companionClass(implicit ctx: Context): Symbol = Symbol.extension_companionClass(self)
    def companionModule(implicit ctx: Context): Symbol = Symbol.extension_companionModule(self)
    def showExtractors(implicit ctx: Context): String = Symbol.extension_showExtractors(self)
    def show(implicit ctx: Context): String = Symbol.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = Symbol.extension_showWith(self, syntaxHighlight)
    def children(implicit ctx: Context): List[Symbol] = Symbol.extension_children(self)
  }

  implicit class TreeOps(self: Tree) {
    def pos(implicit ctx: Context): Position = Tree.extension_pos(self)
    def symbol(implicit ctx: Context): Symbol = Tree.extension_symbol(self)
    def showExtractors(implicit ctx: Context): String = Tree.extension_showExtractors(self)
    def show(implicit ctx: Context): String = Tree.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = Tree.extension_showWith(self, syntaxHighlight)
  }
  implicit class PackageClauseOps(self: PackageClause) {
    def pid(implicit ctx: Context): Ref = PackageClause.extension_pid(self)
    def stats(implicit ctx: Context): List[Tree] = PackageClause.extension_stats(self)
  }
  implicit class ImportOps(self: Import)  {
    def expr(implicit ctx: Context): Term = Import.extension_expr(self)
    def selectors(implicit ctx: Context): List[ImportSelector] = Import.extension_selectors(self)
  }
  implicit class DefinitionOps(self: Definition) {
    def name(implicit ctx: Context): String = Definition.extension_name(self)
  }
  implicit class ClassDefOps(self: ClassDef) {
    def constructor(implicit ctx: Context): DefDef = ClassDef.extension_constructor(self)
    def parents(implicit ctx: Context): List[Tree ] = ClassDef.extension_parents(self)
    def derived(implicit ctx: Context): List[TypeTree] = ClassDef.extension_derived(self)
    def self(implicit ctx: Context): Option[ValDef] = ClassDef.extension_self(self)
    def body(implicit ctx: Context): List[Statement] = ClassDef.extension_body(self)
  }
  implicit class DefDefOps(self: DefDef) {
    def typeParams(implicit ctx: Context): List[TypeDef] = DefDef.extension_typeParams(self)
    def paramss(implicit ctx: Context): List[List[ValDef]] = DefDef.extension_paramss(self)
    def returnTpt(implicit ctx: Context): TypeTree = DefDef.extension_returnTpt(self)
    def rhs(implicit ctx: Context): Option[Term] = DefDef.extension_rhs(self)
  }
  implicit class ValDefOps(self: ValDef) {
    def tpt(implicit ctx: Context): TypeTree = ValDef.extension_tpt(self)
    def rhs(implicit ctx: Context): Option[Term] = ValDef.extension_rhs(self)
  }
  implicit class TypeDefOps(self: TypeDef) {
    def rhs(implicit ctx: Context): Tree = TypeDef.extension_rhs(self)
  }
  implicit class PackageDefOps(self: PackageDef) {
    def owner(implicit ctx: Context): PackageDef = PackageDef.extension_owner(self)
    def members(implicit ctx: Context): List[Statement] = PackageDef.extension_members(self)
  }
  implicit class TermOps(self: Term) {
    def seal(implicit ctx: Context): scala.quoted.Expr[Any] = Term.extension_seal(self)
    def tpe(implicit ctx: Context): Type = Term.extension_tpe(self)
    def underlyingArgument(implicit ctx: Context): Term = Term.extension_underlyingArgument(self)
    def underlying(implicit ctx: Context): Term = Term.extension_underlying(self)
    def etaExpand(implicit ctx: Context): Term = Term.extension_etaExpand(self)
    def appliedTo(arg: Term)(implicit ctx: Context): Term = Term.extension_appliedTo(self, arg)
    def appliedTo(arg: Term, args: Term*)(implicit ctx: Context): Term = Term.extension_appliedTo(self, arg, args: _*)
    def appliedToArgs(args: List[Term])(implicit ctx: Context): Apply = Term.extension_appliedToArgs(self, args)
    def appliedToArgss(argss: List[List[Term]])(implicit ctx: Context): Term = Term.extension_appliedToArgss(self, argss)
    def appliedToNone(implicit ctx: Context): Apply = Term.extension_appliedToNone(self)
    def appliedToType(targ: Type)(implicit ctx: Context): Term = Term.extension_appliedToType(self, targ)
    def appliedToTypes(targs: List[Type])(implicit ctx: Context): Term = Term.extension_appliedToTypes(self, targs)
    def appliedToTypeTrees(targs: List[TypeTree])(implicit ctx: Context): Term = Term.extension_appliedToTypeTrees(self)(targs)
    def select(sym: Symbol)(implicit ctx: Context): Select = Term.extension_select(self)(sym)
  }
  implicit class IdentOps(self: Ident) {
    def name(implicit ctx: Context): String = Ident.extension_name(self)
  }
  implicit class SelectOps(self: Select) {
    def qualifier(implicit ctx: Context): Term = Select.extension_qualifier(self)
    def name(implicit ctx: Context): String = Select.extension_name(self)
    def signature(implicit ctx: Context): Option[Signature] = Select.extension_signature(self)
  }
  implicit class LiteralOps(self: Literal) {
    def constant(implicit ctx: Context): Constant = Literal.extension_constant(self)
  }
  implicit class ThisOps(self: This) {
    def id(implicit ctx: Context): Option[Id] = This.extension_id(self)
  }
  implicit class NewOps(self: New) {
    def tpt(implicit ctx: Context): TypeTree = New.extension_tpt(self)
  }
  implicit class NamedArgOps(self: NamedArg) {
    def name(implicit ctx: Context): String = NamedArg.extension_name(self)
    def value(implicit ctx: Context): Term = NamedArg.extension_value(self)
  }
  implicit class ApplyOps(self: Apply) {
    def fun(implicit ctx: Context): Term = Apply.extension_fun(self)
    def args(implicit ctx: Context): List[Term] = Apply.extension_args(self)
  }
  implicit class TypeApplyOps(self: TypeApply) {
    def fun(implicit ctx: Context): Term = TypeApply.extension_fun(self)
    def args(implicit ctx: Context): List[TypeTree] = TypeApply.extension_args(self)
  }
  implicit class SuperOps(self: Super) {
    def qualifier(implicit ctx: Context): Term = Super.extension_qualifier(self)
    def id(implicit ctx: Context): Option[Id] = Super.extension_id(self)
  }
  implicit class TypedOps(self: Typed) {
    def expr(implicit ctx: Context): Term = Typed.extension_expr(self)
    def tpt(implicit ctx: Context): TypeTree = Typed.extension_tpt(self)
  }
  implicit class AssignOps(self: Assign) {
    def lhs(implicit ctx: Context): Term = Assign.extension_lhs(self)
    def rhs(implicit ctx: Context): Term = Assign.extension_rhs(self)
  }
  implicit class BlockOps(self: Block) {
    def statements(implicit ctx: Context): List[Statement] = Block.extension_statements(self)
    def expr(implicit ctx: Context): Term = Block.extension_expr(self)
  }
  implicit class ClosureOps(self: Closure) {
    def meth(implicit ctx: Context): Term = Closure.extension_meth(self)
    def tpeOpt(implicit ctx: Context): Option[Type] = Closure.extension_tpeOpt(self)
  }
  implicit class IfOps(self: If) {
    def cond(implicit ctx: Context): Term = If.extension_cond(self)
    def thenp(implicit ctx: Context): Term = If.extension_thenp(self)
    def elsep(implicit ctx: Context): Term = If.extension_elsep(self)
  }
  implicit class MatchOps(self: Match) {
    def scrutinee(implicit ctx: Context): Term = Match.extension_scrutinee(self)
    def cases(implicit ctx: Context): List[CaseDef] = Match.extension_cases(self)
  }
  implicit class GivenMatchOps(self: GivenMatch) {
    def cases(implicit ctx: Context): List[CaseDef] = GivenMatch.extension_cases(self)
  }
  implicit class TryOps(self: Try) {
    def body(implicit ctx: Context): Term = Try.extension_body(self)
    def cases(implicit ctx: Context): List[CaseDef] = Try.extension_cases(self)
    def finalizer(implicit ctx: Context): Option[Term] = Try.extension_finalizer(self)
  }
  implicit class ReturnOps(self: Return) {
    def expr(implicit ctx: Context): Term = Return.extension_expr(self)
  }
  implicit class RepeatedOps(self: Repeated) {
    def elems(implicit ctx: Context): List[Term] = Repeated.extension_elems(self)
    def elemtpt(implicit ctx: Context): TypeTree = Repeated.extension_elemtpt(self)
  }
  implicit class InlinedOps(self: Inlined) {
    def call(implicit ctx: Context): Option[Tree ] = Inlined.extension_call(self)
    def bindings(implicit ctx: Context): List[Definition] = Inlined.extension_bindings(self)
    def body(implicit ctx: Context): Term = Inlined.extension_body(self)
  }
  implicit class SelectOuterOps(self: SelectOuter) {
    def qualifier(implicit ctx: Context): Term = SelectOuter.extension_qualifier(self)
    def level(implicit ctx: Context): Int = SelectOuter.extension_level(self)
  }
  implicit class WhileOps(self: While) {
    def cond(implicit ctx: Context): Term = While.extension_cond(self)
    def body(implicit ctx: Context): Term = While.extension_body(self)
  }
  implicit class TypeTreeOps(self: TypeTree) {
    def tpe(implicit ctx: Context): Type = TypeTree.extension_tpe(self)
  }
  implicit class TypeIdentOps(self: TypeIdent) {
    def name(implicit ctx: Context): String = TypeIdent.extension_name(self)
  }
  implicit class TypeSelectOps(self: TypeSelect) {
    def qualifier(implicit ctx: Context): Term = TypeSelect.extension_qualifier(self)
    def name(implicit ctx: Context): String = TypeSelect.extension_name(self)
  }
  implicit class ProjectionOps(self: Projection) {
    def qualifier(implicit ctx: Context): TypeTree = Projection.extension_qualifier(self)
    def name(implicit ctx: Context): String = Projection.extension_name(self)
  }
  implicit class SingletonOps(self: Singleton) {
    def ref(implicit ctx: Context): Term = Singleton.extension_ref(self)
  }
  implicit class RefinedOps(self: Refined) {
    def tpt(implicit ctx: Context): TypeTree = Refined.extension_tpt(self)
    def refinements(implicit ctx: Context): List[Definition] = Refined.extension_refinements(self)
  }
  implicit class AppliedOps(self: Applied) {
    def tpt(implicit ctx: Context): TypeTree = Applied.extension_tpt(self)
    def args(implicit ctx: Context): List[Tree ] = Applied.extension_args(self)
  }
  implicit class AnnotatedOps(self: Annotated) {
    def arg(implicit ctx: Context): TypeTree = Annotated.extension_arg(self)
    def annotation(implicit ctx: Context): Term = Annotated.extension_annotation(self)
  }
  implicit class MatchTypeTreeOps(self: MatchTypeTree) {
    def bound(implicit ctx: Context): Option[TypeTree] = MatchTypeTree.extension_bound(self)
    def selector(implicit ctx: Context): TypeTree = MatchTypeTree.extension_selector(self)
    def cases(implicit ctx: Context): List[TypeCaseDef] = MatchTypeTree.extension_cases(self)
  }
  implicit class ByNameOps(self: ByName) {
    def result(implicit ctx: Context): TypeTree = ByName.extension_result(self)
  }
  implicit class LambdaTypeTreeOps(self: LambdaTypeTree) {
    def tparams(implicit ctx: Context): List[TypeDef] = LambdaTypeTree.extension_tparams(self)
    def body(implicit ctx: Context): Tree = LambdaTypeTree.extension_body(self)
  }
  implicit class TypeBindOps(self: TypeBind) {
    def name(implicit ctx: Context): String = TypeBind.extension_name(self)
    def body(implicit ctx: Context): Tree = TypeBind.extension_body(self)
  }
  implicit class TypeBlockOps(self: TypeBlock) {
    def aliases(implicit ctx: Context): List[TypeDef] = TypeBlock.extension_aliases(self)
    def tpt(implicit ctx: Context): TypeTree = TypeBlock.extension_tpt(self)
  }
  implicit class TypeBoundsTreeOps(self: TypeBoundsTree) {
    def tpe(implicit ctx: Context): TypeBounds = TypeBoundsTree.extension_tpe(self)
    def low(implicit ctx: Context): TypeTree = TypeBoundsTree.extension_low(self)
    def hi(implicit ctx: Context): TypeTree = TypeBoundsTree.extension_hi(self)
  }
  implicit class WildcardTypeTreeOps(self: WildcardTypeTree) {
    def tpe(implicit ctx: Context): TypeOrBounds = WildcardTypeTree.extension_tpe(self)
  }
  implicit class CaseDefOps(self: CaseDef) {
    def pattern(implicit ctx: Context): Tree = CaseDef.extension_pattern(self)
    def guard(implicit ctx: Context): Option[Term] = CaseDef.extension_guard(self)
    def rhs(implicit ctx: Context): Term = CaseDef.extension_rhs(self)
  }
  implicit class TypeCaseDefOps(self: TypeCaseDef) {
    def pattern(implicit ctx: Context): TypeTree = TypeCaseDef.extension_pattern(self)
    def rhs(implicit ctx: Context): TypeTree = TypeCaseDef.extension_rhs(self)
  }
  implicit class BindOps(self: Bind) {
    def name(implicit ctx: Context): String = Bind.extension_name(self)
    def pattern(implicit ctx: Context): Tree = Bind.extension_pattern(self)
  }
  implicit class UnapplyOps(self: Unapply) {
    def fun(implicit ctx: Context): Term = Unapply.extension_fun(self)
    def implicits(implicit ctx: Context): List[Term] = Unapply.extension_implicits(self)
    def patterns(implicit ctx: Context): List[Tree] = Unapply.extension_patterns(self)
  }
  implicit class AlternativesOps(self: Alternatives) {
    def patterns(implicit ctx: Context): List[Tree] = Alternatives.extension_patterns(self)
  }

  implicit class TypeOps(self: Type) {
    def seal(implicit ctx: Context): scala.quoted.Type[_] = Type.extension_seal(self)
    def =:=(that: Type)(implicit ctx: Context): Boolean = Type.extension_=:=(self)(that)
    def <:<(that: Type)(implicit ctx: Context): Boolean = Type.extension_<:<(self)(that)
    def widen(implicit ctx: Context): Type = Type.extension_widen(self)
    def widenTermRefExpr(implicit ctx: Context): Type = Type.extension_widenTermRefExpr(self)
    def dealias(implicit ctx: Context): Type = Type.extension_dealias(self)
    def simplified(implicit ctx: Context): Type = Type.extension_simplified(self)
    def classSymbol(implicit ctx: Context): Option[Symbol] = Type.extension_classSymbol(self)
    def typeSymbol(implicit ctx: Context): Symbol = Type.extension_typeSymbol(self)
    def termSymbol(implicit ctx: Context): Symbol = Type.extension_termSymbol(self)
    def isSingleton(implicit ctx: Context): Boolean = Type.extension_isSingleton(self)
    def memberType(member: Symbol)(implicit ctx: Context): Type = Type.extension_memberType(self)(member)
    def baseClasses(implicit ctx: Context): List[Symbol] = Type.extension_baseClasses(self)
    def baseType(member: Symbol)(implicit ctx: Context): Type = Type.extension_baseType(self)(member)
    def derivesFrom(cls: Symbol)(implicit ctx: Context): Boolean = Type.extension_derivesFrom(self)(cls)
    def isFunctionType(implicit ctx: Context): Boolean = Type.extension_isFunctionType(self)
    def isContextFunctionType(implicit ctx: Context): Boolean = Type.extension_isContextFunctionType(self)
    def isErasedFunctionType(implicit ctx: Context): Boolean = Type.extension_isErasedFunctionType(self)
    def isDependentFunctionType(implicit ctx: Context): Boolean = Type.extension_isDependentFunctionType(self)
    def select(sym: Symbol)(implicit ctx: Context): Type = Type.extension_select(self)(sym)
  }
  implicit class ConstantTypeOps(self: ConstantType) {
    def constant(implicit ctx: Context): Constant = ConstantType.extension_constant(self)
  }
  implicit class TermRefOps(self: TermRef) {
    def qualifier(implicit ctx: Context): TypeOrBounds = TermRef.extension_qualifier(self)
    def name(implicit ctx: Context): String = TermRef.extension_name(self)
  }
  implicit class TypeRefOps(self: TypeRef) {
    def qualifier(implicit ctx: Context): TypeOrBounds = TypeRef.extension_qualifier(self)
    def name(implicit ctx: Context): String = TypeRef.extension_name(self)
    def isOpaqueAlias(implicit  ctx: Context): Boolean = TypeRef.extension_isOpaqueAlias(self)
    def translucentSuperType(implicit ctx: Context): Type = TypeRef.extension_translucentSuperType(self)
  }
  implicit class SuperTypeOps(self: SuperType) {
    def thistpe(implicit ctx: Context): Type = SuperType.extension_thistpe(self)
    def supertpe(implicit ctx: Context): Type = SuperType.extension_supertpe(self)
  }
  implicit class RefinementOps(self: Refinement) {
    def parent(implicit ctx: Context): Type = Refinement.extension_parent(self)
    def name(implicit ctx: Context): String = Refinement.extension_name(self)
    def info(implicit ctx: Context): TypeOrBounds = Refinement.extension_info(self)
  }
  implicit class AppliedTypeOps(self: AppliedType) {
    def tycon(implicit ctx: Context): Type = AppliedType.extension_tycon(self)
    def args(implicit ctx: Context): List[TypeOrBounds ] = AppliedType.extension_args(self)
  }
  implicit class AnnotatedTypeOps(self: AnnotatedType) {
    def underlying(implicit ctx: Context): Type = AnnotatedType.extension_underlying(self)
    def annot(implicit ctx: Context): Term = AnnotatedType.extension_annot(self)
  }
  implicit class AndTypeOps(self: AndType) {
    def left(implicit ctx: Context): Type = AndType.extension_left(self)
    def right(implicit ctx: Context): Type = AndType.extension_right(self)
  }
  implicit class OrTypeOps(self: OrType) {
    def left(implicit ctx: Context): Type = OrType.extension_left(self)
    def right(implicit ctx: Context): Type = OrType.extension_right(self)
  }
  implicit class MatchTypeOps(self: MatchType) {
    def bound(implicit ctx: Context): Type = MatchType.extension_bound(self)
    def scrutinee(implicit ctx: Context): Type = MatchType.extension_scrutinee(self)
    def cases(implicit ctx: Context): List[Type] = MatchType.extension_cases(self)
  }
  implicit class ByNameTypeOps(self: ByNameType) {
    def underlying(implicit ctx: Context): Type = ByNameType.extension_underlying(self)
  }
  implicit class ParamRefOps(self: ParamRef) {
    def binder(implicit ctx: Context): LambdaType[TypeOrBounds] = ParamRef.extension_binder(self)
    def paramNum(implicit ctx: Context): Int = ParamRef.extension_paramNum(self)
  }
  implicit class ThisTypeOps(self: ThisType) {
    def tref(implicit ctx: Context): Type = ThisType.extension_tref(self)
  }
  implicit class RecursiveThisOps(self: RecursiveThis) {
    def binder(implicit ctx: Context): RecursiveType = RecursiveThis.extension_binder(self)
  }
  implicit class RecursiveTypeOps(self: RecursiveType) {
    def underlying(implicit ctx: Context): Type = RecursiveType.extension_underlying(self)
    def recThis(implicit ctx: Context): RecursiveThis = RecursiveType.extension_recThis(self)
  }
  implicit class MethodTypeOps(self: MethodType) {
    def isImplicit: Boolean = MethodType.extension_isImplicit(self)
    def isErased: Boolean = MethodType.extension_isErased(self)
    def param(idx: Int)(implicit ctx: Context): Type = MethodType.extension_param(self)(idx)
    def paramNames(implicit ctx: Context): List[String] = MethodType.extension_paramNames(self)
    def paramTypes(implicit ctx: Context): List[Type] = MethodType.extension_paramTypes(self)
    def resType(implicit ctx: Context): Type = MethodType.extension_resType(self)
  }
  implicit class PolyTypeOps(self: PolyType) {
    def param(self: PolyType)(idx: Int)(implicit ctx: Context): Type = PolyType.extension_param(self)(idx)
    def paramNames(implicit ctx: Context): List[String] = PolyType.extension_paramNames(self)
    def paramBounds(implicit ctx: Context): List[TypeBounds] = PolyType.extension_paramBounds(self)
    def resType(implicit ctx: Context): Type = PolyType.extension_resType(self)
  }
  implicit class TypeLambdaOps(self: TypeLambda) {
    def paramNames(implicit ctx: Context): List[String] = TypeLambda.extension_paramNames(self)
    def paramBounds(implicit ctx: Context): List[TypeBounds] = TypeLambda.extension_paramBounds(self)
    def param(idx: Int)(implicit ctx: Context) : Type = TypeLambda.extension_param(self)(idx)
    def resType(implicit ctx: Context): Type = TypeLambda.extension_resType(self)
  }
  implicit class TypeBoundsOps(self: TypeBounds) {
    def low(implicit ctx: Context): Type = TypeBounds.extension_low(self)
    def hi(implicit ctx: Context): Type = TypeBounds.extension_hi(self)
  }

  implicit class CommentOps(self: Comment) {
    def raw: String = Comment.extension_raw(self)
    def expanded: Option[String] = Comment.extension_expanded(self)
    def usecases: List[(String, Option[DefDef])] = Comment.extension_usecases(self)
  }
}
