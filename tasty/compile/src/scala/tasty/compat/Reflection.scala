package scala.tasty.compat

import scala.quoted.show.SyntaxHighlight

// Reproduces the API of https://github.com/lampepfl/dotty/blob/M1/library/src/scala/tasty/Reflection.scala on top of the Scala 2.x ABI.
// Is required because Scala 2.x doesn't support extension methods (and implicit AnyVal classes in a class):
// https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html
class Reflection(val delegate: scala.tasty.Reflection) {
  import delegate._

  implicit class TreeOps(self: Tree) {
    def pos: Position = TreeMethodsImpl.extension_pos(self)
    def symbol: Symbol = TreeMethodsImpl.extension_symbol(self)
    def showExtractors: String = TreeMethodsImpl.extension_showExtractors(self)
    def show: String = TreeMethodsImpl.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight): String = TreeMethodsImpl.extension_showWith(self, syntaxHighlight)
  }
  implicit class PackageClauseOps(self: PackageClause) {
    def pid: Ref = PackageClauseMethodsImpl.extension_pid(self)
    def stats: List[Tree] = PackageClauseMethodsImpl.extension_stats(self)
  }
  implicit class ImportOps(self: Import)  {
    def expr: Term = ImportMethodsImpl.extension_expr(self)
    def selectors: List[ImportSelector] = ImportMethodsImpl.extension_selectors(self)
  }
  implicit class DefinitionOps(self: Definition) {
    def name: String = DefinitionMethodsImpl.extension_name(self)
  }
  implicit class ClassDefOps(self: ClassDef) {
    def constructor: DefDef = ClassDefMethodsImpl.extension_constructor(self)
    def parents: List[Tree ] = ClassDefMethodsImpl.extension_parents(self)
    def derived: List[TypeTree] = ClassDefMethodsImpl.extension_derived(self)
    def self: Option[ValDef] = ClassDefMethodsImpl.extension_self(self)
    def body: List[Statement] = ClassDefMethodsImpl.extension_body(self)
  }
  implicit class DefDefOps(self: DefDef) {
    def typeParams: List[TypeDef] = DefDefMethodsImpl.extension_typeParams(self)
    def paramss: List[List[ValDef]] = DefDefMethodsImpl.extension_paramss(self)
    def returnTpt: TypeTree = DefDefMethodsImpl.extension_returnTpt(self)
    def rhs: Option[Term] = DefDefMethodsImpl.extension_rhs(self)
  }
  implicit class ValDefOps(self: ValDef) {
    def tpt: TypeTree = ValDefMethodsImpl.extension_tpt(self)
    def rhs: Option[Term] = ValDefMethodsImpl.extension_rhs(self)
  }
  implicit class TypeDefOps(self: TypeDef) {
    def rhs: Tree = TypeDefMethodsImpl.extension_rhs(self)
  }
  implicit class TermOps(self: Term) {
    def seal: scala.quoted.Expr[Any] = TermMethodsImpl.extension_seal(self)
    def sealOpt: Option[scala.quoted.Expr[Any]] = TermMethodsImpl.extension_sealOpt(self)
    def tpe: TypeRepr = TermMethodsImpl.extension_tpe(self)
    def underlyingArgument: Term = TermMethodsImpl.extension_underlyingArgument(self)
    def underlying: Term = TermMethodsImpl.extension_underlying(self)
    def etaExpand: Term = TermMethodsImpl.extension_etaExpand(self)
    def appliedTo(arg: Term): Term = TermMethodsImpl.extension_appliedTo(self, arg)
    def appliedTo(arg: Term, args: Term*): Term = TermMethodsImpl.extension_appliedTo(self, arg, args: _*)
    def appliedToArgs(args: List[Term]): Apply = TermMethodsImpl.extension_appliedToArgs(self, args)
    def appliedToArgss(argss: List[List[Term]]): Term = TermMethodsImpl.extension_appliedToArgss(self, argss)
    def appliedToNone: Apply = TermMethodsImpl.extension_appliedToNone(self)
    def appliedToType(targ: TypeRepr): Term = TermMethodsImpl.extension_appliedToType(self, targ)
    def appliedToTypes(targs: List[TypeRepr]): Term = TermMethodsImpl.extension_appliedToTypes(self, targs)
    def appliedToTypeTrees(targs: List[TypeTree]): Term = TermMethodsImpl.extension_appliedToTypeTrees(self)(targs)
    def select(sym: Symbol): Select = TermMethodsImpl.extension_select(self)(sym)
  }
  implicit class IdentOps(self: Ident) {
    def name: String = IdentMethodsImpl.extension_name(self)
  }
  implicit class SelectOps(self: Select) {
    def qualifier: Term = SelectMethodsImpl.extension_qualifier(self)
    def name: String = SelectMethodsImpl.extension_name(self)
    def signature: Option[Signature] = SelectMethodsImpl.extension_signature(self)
  }
  implicit class LiteralOps(self: Literal) {
    def constant: Constant = LiteralMethodsImpl.extension_constant(self)
  }
  implicit class ThisOps(self: This) {
    def id: Option[String] = ThisMethodsImpl.extension_id(self)
  }
  implicit class NewOps(self: New) {
    def tpt: TypeTree = NewMethodsImpl.extension_tpt(self)
  }
  implicit class NamedArgOps(self: NamedArg) {
    def name: String = NamedArgMethodsImpl.extension_name(self)
    def value: Term = NamedArgMethodsImpl.extension_value(self)
  }
  implicit class ApplyOps(self: Apply) {
    def fun: Term = ApplyMethodsImpl.extension_fun(self)
    def args: List[Term] = ApplyMethodsImpl.extension_args(self)
  }
  implicit class TypeApplyOps(self: TypeApply) {
    def fun: Term = TypeApplyMethodsImpl.extension_fun(self)
    def args: List[TypeTree] = TypeApplyMethodsImpl.extension_args(self)
  }
  implicit class SuperOps(self: Super) {
    def qualifier: Term = SuperMethodsImpl.extension_qualifier(self)
    def id: Option[String] = SuperMethodsImpl.extension_id(self)
    def idPos: Position = SuperMethodsImpl.extension_idPos(self)
  }
  implicit class TypedOps(self: Typed) {
    def expr: Term = TypedMethodsImpl.extension_expr(self)
    def tpt: TypeTree = TypedMethodsImpl.extension_tpt(self)
  }
  implicit class AssignOps(self: Assign) {
    def lhs: Term = AssignMethodsImpl.extension_lhs(self)
    def rhs: Term = AssignMethodsImpl.extension_rhs(self)
  }
  implicit class BlockOps(self: Block) {
    def statements: List[Statement] = BlockMethodsImpl.extension_statements(self)
    def expr: Term = BlockMethodsImpl.extension_expr(self)
  }
  implicit class ClosureOps(self: Closure) {
    def meth: Term = ClosureMethodsImpl.extension_meth(self)
    def tpeOpt: Option[TypeRepr] = ClosureMethodsImpl.extension_tpeOpt(self)
  }
  implicit class IfOps(self: If) {
    def cond: Term = IfMethodsImpl.extension_cond(self)
    def thenp: Term = IfMethodsImpl.extension_thenp(self)
    def elsep: Term = IfMethodsImpl.extension_elsep(self)
  }
  implicit class MatchOps(self: Match) {
    def scrutinee: Term = MatchMethodsImpl.extension_scrutinee(self)
    def cases: List[CaseDef] = MatchMethodsImpl.extension_cases(self)
  }
  implicit class GivenMatchOps(self: GivenMatch) {
    def cases: List[CaseDef] = GivenMatchMethodsImpl.extension_cases(self)
  }
  implicit class TryOps(self: Try) {
    def body: Term = TryMethodsImpl.extension_body(self)
    def cases: List[CaseDef] = TryMethodsImpl.extension_cases(self)
    def finalizer: Option[Term] = TryMethodsImpl.extension_finalizer(self)
  }
  implicit class ReturnOps(self: Return) {
    def expr: Term = ReturnMethodsImpl.extension_expr(self)
  }
  implicit class RepeatedOps(self: Repeated) {
    def elems: List[Term] = RepeatedMethodsImpl.extension_elems(self)
    def elemtpt: TypeTree = RepeatedMethodsImpl.extension_elemtpt(self)
  }
  implicit class InlinedOps(self: Inlined) {
    def call: Option[Tree ] = InlinedMethodsImpl.extension_call(self)
    def bindings: List[Definition] = InlinedMethodsImpl.extension_bindings(self)
    def body: Term = InlinedMethodsImpl.extension_body(self)
  }
  implicit class SelectOuterOps(self: SelectOuter) {
    def qualifier: Term = SelectOuterMethodsImpl.extension_qualifier(self)
    def name: String = SelectOuterMethodsImpl.extension_name(self)
    def level: Int = SelectOuterMethodsImpl.extension_level(self)
  }
  implicit class WhileOps(self: While) {
    def cond: Term = WhileMethodsImpl.extension_cond(self)
    def body: Term = WhileMethodsImpl.extension_body(self)
  }
  implicit class TypeTreeOps(self: TypeTree) {
    def tpe: TypeRepr = TypeTreeMethodsImpl.extension_tpe(self)
  }
  implicit class TypeIdentOps(self: TypeIdent) {
    def name: String = TypeIdentMethodsImpl.extension_name(self)
  }
  implicit class TypeSelectOps(self: TypeSelect) {
    def qualifier: Term = TypeSelectMethodsImpl.extension_qualifier(self)
    def name: String = TypeSelectMethodsImpl.extension_name(self)
  }
  implicit class ProjectionOps(self: Projection) {
    def qualifier: TypeTree = ProjectionMethodsImpl.extension_qualifier(self)
    def name: String = ProjectionMethodsImpl.extension_name(self)
  }
  implicit class SingletonOps(self: Singleton) {
    def ref: Term = SingletonMethodsImpl.extension_ref(self)
  }
  implicit class RefinedOps(self: Refined) {
    def tpt: TypeTree = RefinedMethodsImpl.extension_tpt(self)
    def refinements: List[Definition] = RefinedMethodsImpl.extension_refinements(self)
  }
  implicit class AppliedOps(self: Applied) {
    def tpt: TypeTree = AppliedMethodsImpl.extension_tpt(self)
    def args: List[Tree ] = AppliedMethodsImpl.extension_args(self)
  }
  implicit class AnnotatedOps(self: Annotated) {
    def arg: TypeTree = AnnotatedMethodsImpl.extension_arg(self)
    def annotation: Term = AnnotatedMethodsImpl.extension_annotation(self)
  }
  implicit class MatchTypeTreeOps(self: MatchTypeTree) {
    def bound: Option[TypeTree] = MatchTypeTreeMethodsImpl.extension_bound(self)
    def selector: TypeTree = MatchTypeTreeMethodsImpl.extension_selector(self)
    def cases: List[TypeCaseDef] = MatchTypeTreeMethodsImpl.extension_cases(self)
  }
  implicit class ByNameOps(self: ByName) {
    def result: TypeTree = ByNameMethodsImpl.extension_result(self)
  }
  implicit class LambdaTypeTreeOps(self: LambdaTypeTree) {
    def tparams: List[TypeDef] = LambdaTypeTreeMethodsImpl.extension_tparams(self)
    def body: Tree = LambdaTypeTreeMethodsImpl.extension_body(self)
  }
  implicit class TypeBindOps(self: TypeBind) {
    def name: String = TypeBindMethodsImpl.extension_name(self)
    def body: Tree = TypeBindMethodsImpl.extension_body(self)
  }
  implicit class TypeBlockOps(self: TypeBlock) {
    def aliases: List[TypeDef] = TypeBlockMethodsImpl.extension_aliases(self)
    def tpt: TypeTree = TypeBlockMethodsImpl.extension_tpt(self)
  }
  implicit class TypeBoundsTreeOps(self: TypeBoundsTree) {
    def tpe: TypeBounds = TypeBoundsTreeMethodsImpl.extension_tpe(self)
    def low: TypeTree = TypeBoundsTreeMethodsImpl.extension_low(self)
    def hi: TypeTree = TypeBoundsTreeMethodsImpl.extension_hi(self)
  }
  implicit class WildcardTypeTreeOps(self: WildcardTypeTree) {
    def tpe: TypeRepr = WildcardTypeTreeMethodsImpl.extension_tpe(self)
  }
  implicit class CaseDefOps(self: CaseDef) {
    def pattern: Tree = CaseDefMethodsImpl.extension_pattern(self)
    def guard: Option[Term] = CaseDefMethodsImpl.extension_guard(self)
    def rhs: Term = CaseDefMethodsImpl.extension_rhs(self)
  }
  implicit class TypeCaseDefOps(self: TypeCaseDef) {
    def pattern: TypeTree = TypeCaseDefMethodsImpl.extension_pattern(self)
    def rhs: TypeTree = TypeCaseDefMethodsImpl.extension_rhs(self)
  }
  implicit class BindOps(self: Bind) {
    def name: String = BindMethodsImpl.extension_name(self)
    def pattern: Tree = BindMethodsImpl.extension_pattern(self)
  }
  implicit class UnapplyOps(self: Unapply) {
    def fun: Term = UnapplyMethodsImpl.extension_fun(self)
    def implicits: List[Term] = UnapplyMethodsImpl.extension_implicits(self)
    def patterns: List[Tree] = UnapplyMethodsImpl.extension_patterns(self)
  }
  implicit class AlternativesOps(self: Alternatives) {
    def patterns: List[Tree] = AlternativesMethodsImpl.extension_patterns(self)
  }

  implicit class SimpleSelectorOps(self: SimpleSelector) {
    def name: String = SimpleSelectorMethodsImpl.extension_name(self)
    def namePos: Position = SimpleSelectorMethodsImpl.extension_namePos(self)
  }
  implicit class RenameSelectorOps(self: RenameSelector) {
    def fromName: String = RenameSelectorMethodsImpl.extension_fromName(self)
    def fromPos: Position = RenameSelectorMethodsImpl.extension_fromPos(self)
    def toName: String = RenameSelectorMethodsImpl.extension_toName(self)
    def toPos: Position = RenameSelectorMethodsImpl.extension_toPos(self)
  }
  implicit class OmitSelectorOps(self: OmitSelector) {
    def name: String = OmitSelectorMethodsImpl.extension_name(self)
    def namePos: Position = OmitSelectorMethodsImpl.extension_namePos(self)
  }

  implicit class TypeOps(self: TypeRepr) {
    def showExtractors: String = TypeMethodsImpl.extension_showExtractors(self)
    def show: String = TypeMethodsImpl.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight): String = TypeMethodsImpl.extension_showWith(self, syntaxHighlight)
    def seal: scala.quoted.Type[_] = TypeMethodsImpl.extension_seal(self)
    def =:=(that: TypeRepr): Boolean = TypeMethodsImpl.extension_=:=(self)(that)
    def <:<(that: TypeRepr): Boolean = TypeMethodsImpl.extension_<:<(self)(that)
    def widen: TypeRepr = TypeMethodsImpl.extension_widen(self)
    def widenTermRefExpr: TypeRepr = TypeMethodsImpl.extension_widenTermRefExpr(self)
    def dealias: TypeRepr = TypeMethodsImpl.extension_dealias(self)
    def simplified: TypeRepr = TypeMethodsImpl.extension_simplified(self)
    def classSymbol: Option[Symbol] = TypeMethodsImpl.extension_classSymbol(self)
    def typeSymbol: Symbol = TypeMethodsImpl.extension_typeSymbol(self)
    def termSymbol: Symbol = TypeMethodsImpl.extension_termSymbol(self)
    def isSingleton: Boolean = TypeMethodsImpl.extension_isSingleton(self)
    def memberType(member: Symbol): TypeRepr = TypeMethodsImpl.extension_memberType(self)(member)
    def baseClasses: List[Symbol] = TypeMethodsImpl.extension_baseClasses(self)
    def baseType(member: Symbol): TypeRepr = TypeMethodsImpl.extension_baseType(self)(member)
    def derivesFrom(cls: Symbol): Boolean = TypeMethodsImpl.extension_derivesFrom(self)(cls)
    def isFunctionType: Boolean = TypeMethodsImpl.extension_isFunctionType(self)
    def isContextFunctionType: Boolean = TypeMethodsImpl.extension_isContextFunctionType(self)
    def isErasedFunctionType: Boolean = TypeMethodsImpl.extension_isErasedFunctionType(self)
    def isDependentFunctionType: Boolean = TypeMethodsImpl.extension_isDependentFunctionType(self)
    def select(sym: Symbol): TypeRepr = TypeMethodsImpl.extension_select(self)(sym)
    def appliedTo(targ: TypeRepr): TypeRepr = TypeMethodsImpl.extension_appliedTo(self, targ)
    def appliedTo(targs: List[TypeRepr]): TypeRepr = TypeMethodsImpl.extension_appliedTo(self, targs)
  }
  implicit class ConstantTypeOps(self: ConstantType) {
    def constant: Constant = ConstantTypeMethodsImpl.extension_constant(self)
  }
  implicit class TermRefOps(self: TermRef) {
    def qualifier: TypeRepr = TermRefMethodsImpl.extension_qualifier(self)
    def name: String = TermRefMethodsImpl.extension_name(self)
  }
  implicit class TypeRefOps(self: TypeRef) {
    def qualifier: TypeRepr = TypeRefMethodsImpl.extension_qualifier(self)
    def name: String = TypeRefMethodsImpl.extension_name(self)
    def isOpaqueAlias(implicit  ctx: Context): Boolean = TypeRefMethodsImpl.extension_isOpaqueAlias(self)
    def translucentSuperType: TypeRepr = TypeRefMethodsImpl.extension_translucentSuperType(self)
  }
  implicit class SuperTypeOps(self: SuperType) {
    def thistpe: TypeRepr = SuperTypeMethodsImpl.extension_thistpe(self)
    def supertpe: TypeRepr = SuperTypeMethodsImpl.extension_supertpe(self)
  }
  implicit class RefinementOps(self: Refinement) {
    def parent: TypeRepr = RefinementMethodsImpl.extension_parent(self)
    def name: String = RefinementMethodsImpl.extension_name(self)
    def info: TypeRepr = RefinementMethodsImpl.extension_info(self)
  }
  implicit class AppliedTypeOps(self: AppliedType) {
    def tycon: TypeRepr = AppliedTypeMethodsImpl.extension_tycon(self)
    def args: List[TypeRepr] = AppliedTypeMethodsImpl.extension_args(self)
  }
  implicit class AnnotatedTypeOps(self: AnnotatedType) {
    def underlying: TypeRepr = AnnotatedTypeMethodsImpl.extension_underlying(self)
    def annot: Term = AnnotatedTypeMethodsImpl.extension_annot(self)
  }
  implicit class AndTypeOps(self: AndType) {
    def left: TypeRepr = AndTypeMethodsImpl.extension_left(self)
    def right: TypeRepr = AndTypeMethodsImpl.extension_right(self)
  }
  implicit class OrTypeOps(self: OrType) {
    def left: TypeRepr = OrTypeMethodsImpl.extension_left(self)
    def right: TypeRepr = OrTypeMethodsImpl.extension_right(self)
  }
  implicit class MatchTypeOps(self: MatchType) {
    def bound: TypeRepr = MatchTypeMethodsImpl.extension_bound(self)
    def scrutinee: TypeRepr = MatchTypeMethodsImpl.extension_scrutinee(self)
    def cases: List[TypeRepr] = MatchTypeMethodsImpl.extension_cases(self)
  }
  implicit class ByNameTypeOps(self: ByNameType) {
    def underlying: TypeRepr = ByNameTypeMethodsImpl.extension_underlying(self)
  }
  implicit class ParamRefOps(self: ParamRef) {
    def binder: LambdaType = ParamRefMethodsImpl.extension_binder(self)
    def paramNum: Int = ParamRefMethodsImpl.extension_paramNum(self)
  }
  implicit class ThisTypeOps(self: ThisType) {
    def tref: TypeRepr = ThisTypeMethodsImpl.extension_tref(self)
  }
  implicit class RecursiveThisOps(self: RecursiveThis) {
    def binder: RecursiveType = RecursiveThisMethodsImpl.extension_binder(self)
  }
  implicit class RecursiveTypeOps(self: RecursiveType) {
    def underlying: TypeRepr = RecursiveTypeMethodsImpl.extension_underlying(self)
    def recThis: RecursiveThis = RecursiveTypeMethodsImpl.extension_recThis(self)
  }
  implicit class MethodTypeOps(self: MethodType) {
    def isImplicit: Boolean = MethodTypeMethodsImpl.extension_isImplicit(self)
    def isErased: Boolean = MethodTypeMethodsImpl.extension_isErased(self)
    def param(idx: Int): TypeRepr = MethodTypeMethodsImpl.extension_param(self)(idx)
    def paramNames: List[String] = MethodTypeMethodsImpl.extension_paramNames(self)
    def paramTypes: List[TypeRepr] = MethodTypeMethodsImpl.extension_paramTypes(self)
    def resType: TypeRepr = MethodTypeMethodsImpl.extension_resType(self)
  }
  implicit class PolyTypeOps(self: PolyType) {
    def param(self: PolyType)(idx: Int): TypeRepr = PolyTypeMethodsImpl.extension_param(self)(idx)
    def paramNames: List[String] = PolyTypeMethodsImpl.extension_paramNames(self)
    def paramBounds: List[TypeBounds] = PolyTypeMethodsImpl.extension_paramBounds(self)
    def resType: TypeRepr = PolyTypeMethodsImpl.extension_resType(self)
  }
  implicit class TypeLambdaOps(self: TypeLambda) {
    def paramNames: List[String] = TypeLambdaMethodsImpl.extension_paramNames(self)
    def paramBounds: List[TypeBounds] = TypeLambdaMethodsImpl.extension_paramBounds(self)
    def param(idx: Int) : TypeRepr = TypeLambdaMethodsImpl.extension_param(self)(idx)
    def resType: TypeRepr = TypeLambdaMethodsImpl.extension_resType(self)
  }
  implicit class TypeBoundsOps(self: TypeBounds) {
    def low: TypeRepr = TypeBoundsMethodsImpl.extension_low(self)
    def hi: TypeRepr = TypeBoundsMethodsImpl.extension_hi(self)
  }

  implicit class ConstantOps(self: Constant) {
    def value: Any = ConstantMethodsImpl.extension_value(self)
    def showExtractors: String = ConstantMethodsImpl.extension_showExtractors(self)
    def show: String = ConstantMethodsImpl.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight): String = ConstantMethodsImpl.extension_showWith(self, syntaxHighlight)
  }

  implicit class ImplicitSearchSuccessOps(self: ImplicitSearchSuccess) {
    def tree: Term = ImplicitSearchSuccessMethodsImpl.extension_tree(self)
  }
  implicit class ImplicitSearchFailureOps(self: ImplicitSearchFailure) {
    def explanation: String = ImplicitSearchFailureMethodsImpl.extension_explanation(self)
  }

  implicit class SymbolOps(self: Symbol) {
    def owner: Symbol = SymbolMethodsImpl.extension_owner(self)
    def maybeOwner: Symbol = SymbolMethodsImpl.extension_maybeOwner(self)
    def flags: Flags = SymbolMethodsImpl.extension_flags(self)
    def privateWithin: Option[TypeRepr] = SymbolMethodsImpl.extension_privateWithin(self)
    def protectedWithin: Option[TypeRepr] = SymbolMethodsImpl.extension_protectedWithin(self)
    def name: String = SymbolMethodsImpl.extension_name(self)
    def fullName: String = SymbolMethodsImpl.extension_fullName(self)
    def pos: Position = SymbolMethodsImpl.extension_pos(self)
    def localContext: Context = SymbolMethodsImpl.extension_localContext(self)
    def documentation: Option[Documentation] = SymbolMethodsImpl.extension_documentation(self)
    def tree: Tree = SymbolMethodsImpl.extension_tree(self)
    def annots: List[Term] = SymbolMethodsImpl.extension_annots(self)
    def isDefinedInCurrentRun: Boolean = SymbolMethodsImpl.extension_isDefinedInCurrentRun(self)
    def isLocalDummy: Boolean = SymbolMethodsImpl.extension_isLocalDummy(self)
    def isRefinementClass: Boolean = SymbolMethodsImpl.extension_isRefinementClass(self)
    def isAliasType: Boolean = SymbolMethodsImpl.extension_isAbstractType(self)
    def isAnonymousClass: Boolean = SymbolMethodsImpl.extension_isAnonymousClass(self)
    def isAnonymousFunction: Boolean = SymbolMethodsImpl.extension_isAnonymousFunction(self)
    def isAbstractType: Boolean = SymbolMethodsImpl.extension_isAbstractType(self)
    def isClassConstructor: Boolean = SymbolMethodsImpl.extension_isClassConstructor(self)
    def isType: Boolean = SymbolMethodsImpl.extension_isType(self)
    def isTerm: Boolean = SymbolMethodsImpl.extension_isTerm(self)
    def isPackageDef: Boolean = SymbolMethodsImpl.extension_isPackageDef(self)
    def isClassDef: Boolean = SymbolMethodsImpl.extension_isClassDef(self)
    def isTypeDef: Boolean = SymbolMethodsImpl.extension_isTypeDef(self)
    def isValDef: Boolean = SymbolMethodsImpl.extension_isValDef(self)
    def isDefDef: Boolean = SymbolMethodsImpl.extension_isDefDef(self)
    def isBind: Boolean = SymbolMethodsImpl.extension_isBind(self)
    def isNoSymbol: Boolean = SymbolMethodsImpl.extension_isNoSymbol(self)
    def exists: Boolean = SymbolMethodsImpl.extension_exists(self)
    def fields: List[Symbol] = SymbolMethodsImpl.extension_fields(self)
    def field(name: String): Symbol = SymbolMethodsImpl.extension_field(self)(name)
    def classMethod(name: String): List[Symbol] = SymbolMethodsImpl.extension_classMethod(self)(name)
    def classMethods: List[Symbol] = SymbolMethodsImpl.extension_classMethods(self)
    def method(name: String): List[Symbol] = SymbolMethodsImpl.extension_method(self)(name)
    def methods: List[Symbol] = SymbolMethodsImpl.extension_methods(self)
    def caseFields: List[Symbol] = SymbolMethodsImpl.extension_caseFields(self)
    def isTypeParam: Boolean = SymbolMethodsImpl.extension_isTypeParam(self)
    def signature: Signature = SymbolMethodsImpl.extension_signature(self)
    def moduleClass: Symbol = SymbolMethodsImpl.extension_moduleClass(self)
    def companionClass: Symbol = SymbolMethodsImpl.extension_companionClass(self)
    def companionModule: Symbol = SymbolMethodsImpl.extension_companionModule(self)
    def showExtractors: String = SymbolMethodsImpl.extension_showExtractors(self)
    def show: String = SymbolMethodsImpl.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight): String = SymbolMethodsImpl.extension_showWith(self, syntaxHighlight)
    def children: List[Symbol] = SymbolMethodsImpl.extension_children(self)
  }

  implicit class SignatureOps(self: Signature) {
    def paramSigs: List[Any] = SignatureMethodsImpl.extension_paramSigs(self)
    def resultSig: String = SignatureMethodsImpl.extension_resultSig(self)
  }

  implicit class FlagsOps(self: Flags) {
    def is(that: Flags): Boolean = FlagsMethodsImpl.extension_is(self)(that)
    def |(that: Flags): Flags = FlagsMethodsImpl.extension_|(self)(that)
    def &(that: Flags): Flags = FlagsMethodsImpl.extension_&(self)(that)
    def showExtractors: String = FlagsMethodsImpl.extension_showExtractors(self)
    def show: String = FlagsMethodsImpl.extension_show(self)
    def showWith(syntaxHighlight: SyntaxHighlight): String = FlagsMethodsImpl.extension_showWith(self, syntaxHighlight)
  }

  implicit class PositionOps(self: Position) {
    def start: Int = PositionMethodsImpl.extension_start(self)
    def end: Int = PositionMethodsImpl.extension_end(self)
    def exists: Boolean = PositionMethodsImpl.extension_exists(self)
    def sourceFile: SourceFile = PositionMethodsImpl.extension_sourceFile(self)
    def startLine: Int = PositionMethodsImpl.extension_startLine(self)
    def endLine: Int = PositionMethodsImpl.extension_endLine(self)
    def startColumn: Int = PositionMethodsImpl.extension_startColumn(self)
    def endColumn: Int = PositionMethodsImpl.extension_endColumn(self)
    def sourceCode: String = PositionMethodsImpl.extension_sourceCode(self)
  }

  implicit class SourceFileOps(self: SourceFile) {
    def jpath: java.nio.file.Path = SourceFileMethodsImpl.extension_jpath(self)
    def content: String = SourceFileMethodsImpl.extension_content(self)
  }

  implicit class DocumentationOps(self: Documentation) {
    def raw: String = DocumentationMethodsImpl.extension_raw(self)
    def expanded: Option[String] = DocumentationMethodsImpl.extension_expanded(self)
    def usecases: List[(String, Option[DefDef])] = DocumentationMethodsImpl.extension_usecases(self)
  }
}
