package scala.tasty.compat

import scala.quoted.show.SyntaxHighlight
import scala.tasty.reflect.CompilerInterface

// Reproduces the API of https://github.com/lampepfl/dotty/blob/0.22.0-RC1/library/src/scala/tasty/Reflection.scala on top of the Scala 2.x ABI.
// Is required because Scala 2.x doesn't support extension methods (and implicit AnyVal classes in a class):
// https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html
class Reflection(v: CompilerInterface) extends scala.tasty.Reflection(v) {

  implicit class CommentOpsExt(self: Comment) {
    def raw: String = CommentOps.raw(self)
    def expanded: Option[String] = CommentOps.expanded(self)
    def usecases: List[(String, Option[DefDef])] = CommentOps.usecases(self)
  }

  implicit class ConstantOpsExt(self: Constant) {
    def value: Any = ConstantOps.value(self)
  }

  implicit class ContextOpsExt(self: Context) {
    def owner: Symbol = ContextOps.owner(self)
    def source: java.nio.file.Path = ContextOps.source(self)
    def requiredPackage(path: String): Symbol = ContextOps.requiredPackage(self, path)
    def requiredClass(path: String): Symbol = ContextOps.requiredClass(self, path)
    def requiredModule(path: String): Symbol = ContextOps.requiredModule(self, path)
    def requiredMethod(path: String): Symbol = ContextOps.requiredMethod(self, path)
  }

  implicit class FlagsOpsExt(self: Flags) {
    def is(that: Flags): Boolean = FlagsOps.is(self)(that)
    def |(that: Flags): Flags = FlagsOps.|(self)(that)
    def &(that: Flags): Flags = FlagsOps.&(self)(that)
  }

  implicit class IdOpsExt(self: Id) {
    def pos(implicit ctx: Context): Position = IdOps.pos(self)
    def name(implicit ctx: Context): String = IdOps.name(self)
  }

  implicit class successOpsExt(self: ImplicitSearchSuccess) {
    def tree(implicit ctx: Context): Term = successOps.tree(self)
  }
  implicit class failureOpsExt(self: ImplicitSearchFailure) {
    def explanation(implicit ctx: Context): String = failureOps.explanation(self)
  }

  implicit class simpleSelectorOpsExt(self: SimpleSelector) {
    def selection(implicit ctx: Context): Id = simpleSelectorOps.selection(self)
  }
  implicit class renameSelectorOpsExt(self: RenameSelector) {
    def from(implicit ctx: Context): Id = renameSelectorOps.from(self)
    def to(implicit ctx: Context): Id = renameSelectorOps.to(self)
  }
  implicit class omitSelectorOpsExt(self: OmitSelector) {
    def omitted(implicit ctx: Context): Id = omitSelectorOps.omitted(self)
  }

  implicit class positionOpsExt(self: Position) {
    def start: Int = positionOps.start(self)
    def end: Int = positionOps.end(self)
    def exists: Boolean = positionOps.exists(self)
    def sourceFile: SourceFile = positionOps.sourceFile(self)
    def startLine: Int = positionOps.startLine(self)
    def endLine: Int = positionOps.endLine(self)
    def startColumn: Int = positionOps.startColumn(self)
    def endColumn: Int = positionOps.endColumn(self)
    def sourceCode: String = positionOps.sourceCode(self)
  }
  implicit class sourceFileOpsExt(self: SourceFile) {
    def jpath: java.nio.file.Path = sourceFileOps.jpath(self)
    def content: String = sourceFileOps.content(self)
  }

  implicit class signatureOpsExt(self: Signature) {
    def paramSigs: List[Any] = signatureOps.paramSigs(self)
    def resultSig: String = signatureOps.resultSig(self)
  }

  implicit class SymbolOpsExt(self: Symbol) {
    def owner(implicit ctx: Context): Symbol = SymbolOps.owner(self)
    def maybeOwner(implicit ctx: Context): Symbol = SymbolOps.maybeOwner(self)
    def flags(implicit ctx: Context): Flags = SymbolOps.flags(self)
    def privateWithin(implicit ctx: Context): Option[Type] = SymbolOps.privateWithin(self)
    def protectedWithin(implicit ctx: Context): Option[Type] = SymbolOps.protectedWithin(self)
    def name(implicit ctx: Context): String = SymbolOps.name(self)
    def fullName(implicit ctx: Context): String = SymbolOps.fullName(self)
    def pos(implicit ctx: Context): Position = SymbolOps.pos(self)
    def localContext(implicit ctx: Context): Context = SymbolOps.localContext(self)
    def comment(implicit ctx: Context): Option[Comment] = SymbolOps.comment(self)
    def tree(implicit ctx: Context): Tree = SymbolOps.tree(self)
    def annots(implicit ctx: Context): List[Term] = SymbolOps.annots(self)
    def isDefinedInCurrentRun(implicit ctx: Context): Boolean = SymbolOps.isDefinedInCurrentRun(self)
    def isLocalDummy(implicit ctx: Context): Boolean = SymbolOps.isLocalDummy(self)
    def isRefinementClass(implicit ctx: Context): Boolean = SymbolOps.isRefinementClass(self)
    def isAliasType(implicit ctx: Context): Boolean = SymbolOps.isAbstractType(self)
    def isAnonymousClass(implicit ctx: Context): Boolean = SymbolOps.isAnonymousClass(self)
    def isAnonymousFunction(implicit ctx: Context): Boolean = SymbolOps.isAnonymousFunction(self)
    def isAbstractType(implicit ctx: Context): Boolean = SymbolOps.isAbstractType(self)
    def isClassConstructor(implicit ctx: Context): Boolean = SymbolOps.isClassConstructor(self)
    def isType(implicit ctx: Context): Boolean = SymbolOps.isType(self)
    def isTerm(implicit ctx: Context): Boolean = SymbolOps.isTerm(self)
    def isPackageDef(implicit ctx: Context): Boolean = SymbolOps.isPackageDef(self)
    def isClassDef(implicit ctx: Context): Boolean = SymbolOps.isClassDef(self)
    def isTypeDef(implicit ctx: Context): Boolean = SymbolOps.isTypeDef(self)
    def isValDef(implicit ctx: Context): Boolean = SymbolOps.isValDef(self)
    def isDefDef(implicit ctx: Context): Boolean = SymbolOps.isDefDef(self)
    def isBind(implicit ctx: Context): Boolean = SymbolOps.isBind(self)
    def isNoSymbol(implicit ctx: Context): Boolean = SymbolOps.isNoSymbol(self)
    def exists(implicit ctx: Context): Boolean = SymbolOps.exists(self)
    def fields(implicit ctx: Context): List[Symbol] = SymbolOps.fields(self)
    def field(name: String)(implicit ctx: Context): Symbol = SymbolOps.field(self)(name)
    def classMethod(name: String)(implicit ctx: Context): List[Symbol] = SymbolOps.classMethod(self)(name)
    def classMethods(implicit ctx: Context): List[Symbol] = SymbolOps.classMethods(self)
    def method(name: String)(implicit ctx: Context): List[Symbol] = SymbolOps.method(self)(name)
    def methods(implicit ctx: Context): List[Symbol] = SymbolOps.methods(self)
    def caseFields(implicit ctx: Context): List[Symbol] = SymbolOps.caseFields(self)
    def isTypeParam(implicit ctx: Context): Boolean = SymbolOps.isTypeParam(self)
    def signature(implicit ctx: Context): Signature = SymbolOps.signature(self)
    def moduleClass(implicit ctx: Context): Symbol = SymbolOps.moduleClass(self)
    def companionClass(implicit ctx: Context): Symbol = SymbolOps.companionClass(self)
    def companionModule(implicit ctx: Context): Symbol = SymbolOps.companionModule(self)
    def showExtractors(implicit ctx: Context): String = SymbolOps.showExtractors(self)
    def show(implicit ctx: Context): String = SymbolOps.show(self)
    def showWith(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = SymbolOps.showWith(self, syntaxHighlight)
    def children(implicit ctx: Context): List[Symbol] = SymbolOps.children(self)
  }

  implicit class TreeOpsExt(self: Tree) {
    def pos(implicit ctx: Context): Position = TreeOps.pos(self)
    def symbol(implicit ctx: Context): Symbol = TreeOps.symbol(self)
    def showExtractors(implicit ctx: Context): String = TreeOps.showExtractors(self)
    def show(implicit ctx: Context): String = TreeOps.show(self)
    def showWith(syntaxHighlight: SyntaxHighlight)(implicit ctx: Context): String = TreeOps.showWith(self, syntaxHighlight)
  }
  implicit class PackageClauseOpsExt(self: PackageClause) {
    def pid(implicit ctx: Context): Ref = PackageClauseOps.pid(self)
    def stats(implicit ctx: Context): List[Tree] = PackageClauseOps.stats(self)
  }
  implicit class ImportOpsExt(self: Import)  {
    def expr(implicit ctx: Context): Term = ImportOps.expr(self)
    def selectors(implicit ctx: Context): List[ImportSelector] = ImportOps.selectors(self)
  }
  implicit class DefinitionOpsExt(self: Definition) {
    def name(implicit ctx: Context): String = DefinitionOps.name(self)
  }
  implicit class ClassDefOpsExt(self: ClassDef) {
    def constructor(implicit ctx: Context): DefDef = ClassDefOps.constructor(self)
    def parents(implicit ctx: Context): List[Tree ] = ClassDefOps.parents(self)
    def derived(implicit ctx: Context): List[TypeTree] = ClassDefOps.derived(self)
    def self(implicit ctx: Context): Option[ValDef] = ClassDefOps.self(self)
    def body(implicit ctx: Context): List[Statement] = ClassDefOps.body(self)
  }
  implicit class DefDefOpsExt(self: DefDef) {
    def typeParams(implicit ctx: Context): List[TypeDef] = DefDefOps.typeParams(self)
    def paramss(implicit ctx: Context): List[List[ValDef]] = DefDefOps.paramss(self)
    def returnTpt(implicit ctx: Context): TypeTree = DefDefOps.returnTpt(self)
    def rhs(implicit ctx: Context): Option[Term] = DefDefOps.rhs(self)
  }
  implicit class ValDefOpsExt(self: ValDef) {
    def tpt(implicit ctx: Context): TypeTree = ValDefOps.tpt(self)
    def rhs(implicit ctx: Context): Option[Term] = ValDefOps.rhs(self)
  }
  implicit class TypeDefOpsExt(self: TypeDef) {
    def rhs(implicit ctx: Context): Tree = TypeDefOps.rhs(self)
  }
  implicit class PackageDefOpsExt(self: PackageDef) {
    def owner(implicit ctx: Context): PackageDef = PackageDefOps.owner(self)
    def members(implicit ctx: Context): List[Statement] = PackageDefOps.members(self)
  }
  implicit class TermOpsExt(self: Term) {
    def seal(implicit ctx: Context): scala.quoted.Expr[Any] = TermOps.seal(self)
    def tpe(implicit ctx: Context): Type = TermOps.tpe(self)
    def underlyingArgument(implicit ctx: Context): Term = TermOps.underlyingArgument(self)
    def underlying(implicit ctx: Context): Term = TermOps.underlying(self)
    def etaExpand(implicit ctx: Context): Term = TermOps.etaExpand(self)
    def appliedTo(arg: Term)(implicit ctx: Context): Term = TermOps.appliedTo(self, arg)
    def appliedTo(arg: Term, args: Term*)(implicit ctx: Context): Term = TermOps.appliedTo(self, arg, args: _*)
    def appliedToArgs(args: List[Term])(implicit ctx: Context): Apply = TermOps.appliedToArgs(self, args)
    def appliedToArgss(argss: List[List[Term]])(implicit ctx: Context): Term = TermOps.appliedToArgss(self, argss)
    def appliedToNone(implicit ctx: Context): Apply = TermOps.appliedToNone(self)
    def appliedToType(targ: Type)(implicit ctx: Context): Term = TermOps.appliedToType(self, targ)
    def appliedToTypes(targs: List[Type])(implicit ctx: Context): Term = TermOps.appliedToTypes(self, targs)
    def appliedToTypeTrees(targs: List[TypeTree])(implicit ctx: Context): Term = TermOps.appliedToTypeTrees(self)(targs)
    def select(sym: Symbol)(implicit ctx: Context): Select = TermOps.select(self)(sym)
  }
  implicit class IdentOpsExt(self: Ident) {
    def name(implicit ctx: Context): String = IdentOps.name(self)
  }
  implicit class SelectOpsExt(self: Select) {
    def qualifier(implicit ctx: Context): Term = SelectOps.qualifier(self)
    def name(implicit ctx: Context): String = SelectOps.name(self)
    def signature(implicit ctx: Context): Option[Signature] = SelectOps.signature(self)
  }
  implicit class LiteralOpsExt(self: Literal) {
    def constant(implicit ctx: Context): Constant = LiteralOps.constant(self)
  }
  implicit class ThisOpsExt(self: This) {
    def id(implicit ctx: Context): Option[Id] = ThisOps.id(self)
  }
  implicit class NewOpsExt(self: New) {
    def tpt(implicit ctx: Context): TypeTree = NewOps.tpt(self)
  }
  implicit class NamedArgOpsExt(self: NamedArg) {
    def name(implicit ctx: Context): String = NamedArgOps.name(self)
    def value(implicit ctx: Context): Term = NamedArgOps.value(self)
  }
  implicit class ApplyOpsExt(self: Apply) {
    def fun(implicit ctx: Context): Term = ApplyOps.fun(self)
    def args(implicit ctx: Context): List[Term] = ApplyOps.args(self)
  }
  implicit class TypeApplyOpsExt(self: TypeApply) {
    def fun(implicit ctx: Context): Term = TypeApplyOps.fun(self)
    def args(implicit ctx: Context): List[TypeTree] = TypeApplyOps.args(self)
  }
  implicit class SuperOpsExt(self: Super) {
    def qualifier(implicit ctx: Context): Term = SuperOps.qualifier(self)
    def id(implicit ctx: Context): Option[Id] = SuperOps.id(self)
  }
  implicit class TypedOpsExt(self: Typed) {
    def expr(implicit ctx: Context): Term = TypedOps.expr(self)
    def tpt(implicit ctx: Context): TypeTree = TypedOps.tpt(self)
  }
  implicit class AssignOps(self: Assign) {
    def lhs(implicit ctx: Context): Term = ???
    def rhs(implicit ctx: Context): Term = ???
  }
  implicit class BlockOpsExt(self: Block) {
    def statements(implicit ctx: Context): List[Statement] = BlockOps.statements(self)
    def expr(implicit ctx: Context): Term = BlockOps.expr(self)
  }
  implicit class ClosureOpsExt(self: Closure) {
    def meth(implicit ctx: Context): Term = ClosureOps.meth(self)
    def tpeOpt(implicit ctx: Context): Option[Type] = ClosureOps.tpeOpt(self)
  }
  implicit class IfOpsExt(self: If) {
    def cond(implicit ctx: Context): Term = IfOps.cond(self)
    def thenp(implicit ctx: Context): Term = IfOps.thenp(self)
    def elsep(implicit ctx: Context): Term = IfOps.elsep(self)
  }
  implicit class MatchOpsExt(self: Match) {
    def scrutinee(implicit ctx: Context): Term = MatchOps.scrutinee(self)
    def cases(implicit ctx: Context): List[CaseDef] = MatchOps.cases(self)
  }
  implicit class GivenMatchOpsExt(self: GivenMatch) {
    def cases(implicit ctx: Context): List[CaseDef] = GivenMatchOps.cases(self)
  }
  implicit class TryOpsExt(self: Try) {
    def body(implicit ctx: Context): Term = TryOps.body(self)
    def cases(implicit ctx: Context): List[CaseDef] = TryOps.cases(self)
    def finalizer(implicit ctx: Context): Option[Term] = TryOps.finalizer(self)
  }
  implicit class ReturnOpsExt(self: Return) {
    def expr(implicit ctx: Context): Term = ReturnOps.expr(self)
  }
  implicit class RepeatedOpsExt(self: Repeated) {
    def elems(implicit ctx: Context): List[Term] = RepeatedOps.elems(self)
    def elemtpt(implicit ctx: Context): TypeTree = RepeatedOps.elemtpt(self)
  }
  implicit class InlinedOpsExt(self: Inlined) {
    def call(implicit ctx: Context): Option[Tree ] = InlinedOps.call(self)
    def bindings(implicit ctx: Context): List[Definition] = InlinedOps.bindings(self)
    def body(implicit ctx: Context): Term = InlinedOps.body(self)
  }
  implicit class SelectOuterOpsExt(self: SelectOuter) {
    def qualifier(implicit ctx: Context): Term = SelectOuterOps.qualifier(self)
    def level(implicit ctx: Context): Int = SelectOuterOps.level(self)
  }
  implicit class WhileOpsExt(self: While) {
    def cond(implicit ctx: Context): Term = WhileOps.cond(self)
    def body(implicit ctx: Context): Term = WhileOps.body(self)
  }
  implicit class TypeTreeOpsExt(self: TypeTree) {
    def tpe(implicit ctx: Context): Type = TypeTreeOps.tpe(self)
  }
  implicit class TypeIdentOpsExt(self: TypeIdent) {
    def name(implicit ctx: Context): String = TypeIdentOps.name(self)
  }
  implicit class TypeSelectOpsExt(self: TypeSelect) {
    def qualifier(implicit ctx: Context): Term = TypeSelectOps.qualifier(self)
    def name(implicit ctx: Context): String = TypeSelectOps.name(self)
  }
  implicit class ProjectionOpsExt(self: Projection) {
    def qualifier(implicit ctx: Context): TypeTree = ProjectionOps.qualifier(self)
    def name(implicit ctx: Context): String = ProjectionOps.name(self)
  }
  implicit class SingletonOpsExt(self: Singleton) {
    def ref(implicit ctx: Context): Term = SingletonOps.ref(self)
  }
  implicit class RefinedOpsExt(self: Refined) {
    def tpt(implicit ctx: Context): TypeTree = RefinedOps.tpt(self)
    def refinements(implicit ctx: Context): List[Definition] = RefinedOps.refinements(self)
  }
  implicit class AppliedOpsExt(self: Applied) {
    def tpt(implicit ctx: Context): TypeTree = AppliedOps.tpt(self)
    def args(implicit ctx: Context): List[Tree ] = AppliedOps.args(self)
  }
  implicit class AnnotatedOpsExt(self: Annotated) {
    def arg(implicit ctx: Context): TypeTree = AnnotatedOps.arg(self)
    def annotation(implicit ctx: Context): Term = AnnotatedOps.annotation(self)
  }
  implicit class MatchTypeTreeOpsExt(self: MatchTypeTree) {
    def bound(implicit ctx: Context): Option[TypeTree] = MatchTypeTreeOps.bound(self)
    def selector(implicit ctx: Context): TypeTree = MatchTypeTreeOps.selector(self)
    def cases(implicit ctx: Context): List[TypeCaseDef] = MatchTypeTreeOps.cases(self)
  }
  implicit class ByNameOpsExt(self: ByName) {
    def result(implicit ctx: Context): TypeTree = ByNameOps.result(self)
  }
  implicit class LambdaTypeTreeOpsExt(self: LambdaTypeTree) {
    def tparams(implicit ctx: Context): List[TypeDef] = LambdaTypeTreeOps.tparams(self)
    def body(implicit ctx: Context): Tree  = LambdaTypeTreeOps.body(self)
  }
  implicit class TypeBindOpsExt(self: TypeBind) {
    def name(implicit ctx: Context): String = TypeBindOps.name(self)
    def body(implicit ctx: Context): Tree  = TypeBindOps.body(self)
  }
  implicit class TypeBlockOpsExt(self: TypeBlock) {
    def aliases(implicit ctx: Context): List[TypeDef] = TypeBlockOps.aliases(self)
    def tpt(implicit ctx: Context): TypeTree = TypeBlockOps.tpt(self)
  }
  implicit class TypeBoundsTreeOpsExt(self: TypeBoundsTree) {
    def tpe(implicit ctx: Context): TypeBounds = TypeBoundsTreeOps.tpe(self)
    def low(implicit ctx: Context): TypeTree = TypeBoundsTreeOps.low(self)
    def hi(implicit ctx: Context): TypeTree = TypeBoundsTreeOps.hi(self)
  }
  implicit class WildcardTypeTreeOpsExt(self: WildcardTypeTree) {
    def tpe(implicit ctx: Context): TypeOrBounds = WildcardTypeTreeOps.tpe(self)
  }
  implicit class CaseDefOpsExt(self: CaseDef) {
    def pattern(implicit ctx: Context): Tree = CaseDefOps.pattern(self)
    def guard(implicit ctx: Context): Option[Term] = CaseDefOps.guard(self)
    def rhs(implicit ctx: Context): Term = CaseDefOps.rhs(self)
  }
  implicit class TypeCaseDefOpsExt(self: TypeCaseDef) {
    def pattern(implicit ctx: Context): TypeTree = TypeCaseDefOps.pattern(self)
    def rhs(implicit ctx: Context): TypeTree = TypeCaseDefOps.rhs(self)
  }
  implicit class BindOpsExt(self: Bind) {
    def name(implicit ctx: Context): String = BindOps.name(self)
    def pattern(implicit ctx: Context): Tree = BindOps.pattern(self)
  }
  implicit class UnapplyOpsExt(self: Unapply) {
    def fun(implicit ctx: Context): Term = UnapplyOps.fun(self)
    def implicits(implicit ctx: Context): List[Term] = UnapplyOps.implicits(self)
    def patterns(implicit ctx: Context): List[Tree] = UnapplyOps.patterns(self)
  }
  implicit class AlternativesOpsExt(self: Alternatives) {
    def patterns(implicit ctx: Context): List[Tree] = AlternativesOps.patterns(self)
  }

  implicit class TypeOpsExt(self: Type) {
    def seal(implicit ctx: Context): scala.quoted.Type[_] = TypeOps.seal(self)
    def =:=(that: Type)(implicit ctx: Context): Boolean = TypeOps.=:=(self)(that)
    def <:<(that: Type)(implicit ctx: Context): Boolean = TypeOps.<:<(self)(that)
    def widen(implicit ctx: Context): Type = TypeOps.widen(self)
    def widenTermRefExpr(implicit ctx: Context): Type = TypeOps.widenTermRefExpr(self)
    def dealias(implicit ctx: Context): Type = TypeOps.dealias(self)
    def simplified(implicit ctx: Context): Type = TypeOps.simplified(self)
    def classSymbol(implicit ctx: Context): Option[Symbol] = TypeOps.classSymbol(self)
    def typeSymbol(implicit ctx: Context): Symbol = TypeOps.typeSymbol(self)
    def termSymbol(implicit ctx: Context): Symbol = TypeOps.termSymbol(self)
    def isSingleton(implicit ctx: Context): Boolean = TypeOps.isSingleton(self)
    def memberType(member: Symbol)(implicit ctx: Context): Type = TypeOps.memberType(self)(member)
    def derivesFrom(cls: Symbol)(implicit ctx: Context): Boolean = TypeOps.derivesFrom(self)(cls)
    def isFunctionType(implicit ctx: Context): Boolean = TypeOps.isFunctionType(self)
    def isContextFunctionType(implicit ctx: Context): Boolean = TypeOps.isContextFunctionType(self)
    def isErasedFunctionType(implicit ctx: Context): Boolean = TypeOps.isErasedFunctionType(self)
    def isDependentFunctionType(implicit ctx: Context): Boolean = TypeOps.isDependentFunctionType(self)
  }
  implicit class ConstantTypeOpsExt(self: ConstantType) {
    def constant(implicit ctx: Context): Constant = ConstantTypeOps.constant(self)
  }
  implicit class TermRefOpsExt(self: TermRef) {
    def qualifier(implicit ctx: Context): TypeOrBounds  = TermRefOps.qualifier(self)
    def name(implicit ctx: Context): String = TermRefOps.name(self)
  }
  implicit class TypeRefOpsExt(self: TypeRef) {
    def qualifier(implicit ctx: Context): TypeOrBounds  = TypeRefOps.qualifier(self)
    def name(implicit ctx: Context): String = TypeRefOps.name(self)
    def isOpaqueAlias(implicit  ctx: Context): Boolean = TypeRefOps.isOpaqueAlias(self)
    def translucentSuperType(implicit ctx: Context): Type = TypeRefOps.translucentSuperType(self)
  }
  implicit class SuperTypeOpsExt(self: SuperType) {
    def thistpe(implicit ctx: Context): Type = SuperTypeOps.thistpe(self)
    def supertpe(implicit ctx: Context): Type = SuperTypeOps.supertpe(self)
  }
  implicit class RefinementOpsExt(self: Refinement) {
    def parent(implicit ctx: Context): Type = RefinementOps.parent(self)
    def name(implicit ctx: Context): String = RefinementOps.name(self)
    def info(implicit ctx: Context): TypeOrBounds = RefinementOps.info(self)
  }
  implicit class AppliedTypeOpsExt(self: AppliedType) {
    def tycon(implicit ctx: Context): Type = AppliedTypeOps.tycon(self)
    def args(implicit ctx: Context): List[TypeOrBounds ] = AppliedTypeOps.args(self)
  }
  implicit class AnnotatedTypeOpsExt(self: AnnotatedType) {
    def underlying(implicit ctx: Context): Type = AnnotatedTypeOps.underlying(self)
    def annot(implicit ctx: Context): Term = AnnotatedTypeOps.annot(self)
  }
  implicit class AndTypeOpsExt(self: AndType) {
    def left(implicit ctx: Context): Type = AndTypeOps.left(self)
    def right(implicit ctx: Context): Type = AndTypeOps.right(self)
  }
  implicit class OrTypeOpsExt(self: OrType) {
    def left(implicit ctx: Context): Type = OrTypeOps.left(self)
    def right(implicit ctx: Context): Type = OrTypeOps.right(self)
  }
  implicit class MatchTypeOpsExt(self: MatchType) {
    def bound(implicit ctx: Context): Type = MatchTypeOps.bound(self)
    def scrutinee(implicit ctx: Context): Type = MatchTypeOps.scrutinee(self)
    def cases(implicit ctx: Context): List[Type] = MatchTypeOps.cases(self)
  }
  implicit class ByNameTypeOpsExt(self: ByNameType) {
    def underlying(implicit ctx: Context): Type = ByNameTypeOps.underlying(self)
  }
  implicit class ParamRefOpsExt(self: ParamRef) {
    def binder(implicit ctx: Context): LambdaType[TypeOrBounds] = ParamRefOps.binder(self)
    def paramNum(implicit ctx: Context): Int = ParamRefOps.paramNum(self)
  }
  implicit class ThisTypeOpsExt(self: ThisType) {
    def tref(implicit ctx: Context): Type = ThisTypeOps.tref(self)
  }
  implicit class RecursiveThisOpsExt(self: RecursiveThis) {
    def binder(implicit ctx: Context): RecursiveType = RecursiveThisOps.binder(self)
  }
  implicit class RecursiveTypeOpsExt(self: RecursiveType) {
    def underlying(implicit ctx: Context): Type = RecursiveTypeOps.underlying(self)
    def recThis(implicit ctx: Context): RecursiveThis = RecursiveTypeOps.recThis(self)
  }
  implicit class MethodTypeOpsExt(self: MethodType) {
    def isImplicit: Boolean = MethodTypeOps.isImplicit(self)
    def isErased: Boolean = MethodTypeOps.isErased(self)
    def param(idx: Int)(implicit ctx: Context): Type = MethodTypeOps.param(self)(idx)
    def paramNames(implicit ctx: Context): List[String] = MethodTypeOps.paramNames(self)
    def paramTypes(implicit ctx: Context): List[Type] = MethodTypeOps.paramTypes(self)
    def resType(implicit ctx: Context): Type = MethodTypeOps.resType(self)
  }
  implicit class PolyTypeOpsExt(self: PolyType) {
    def param(self: PolyType)(idx: Int)(implicit ctx: Context): Type = PolyTypeOps.param(self)(idx)
    def paramNames(implicit ctx: Context): List[String] = PolyTypeOps.paramNames(self)
    def paramBounds(implicit ctx: Context): List[TypeBounds] = PolyTypeOps.paramBounds(self)
    def resType(implicit ctx: Context): Type = PolyTypeOps.resType(self)
  }
  implicit class TypeLambdaOpsExt(self: TypeLambda) {
    def paramNames(implicit ctx: Context): List[String] = TypeLambdaOps.paramNames(self)
    def paramBounds(implicit ctx: Context): List[TypeBounds] = TypeLambdaOps.paramBounds(self)
    def param(idx: Int)(implicit ctx: Context) : Type = TypeLambdaOps.param(self)(idx)
    def resType(implicit ctx: Context): Type = TypeLambdaOps.resType(self)
  }
  implicit class TypeBoundsOpsExt(self: TypeBounds) {
    def low(implicit ctx: Context): Type = TypeBoundsOps.low(self)
    def hi(implicit ctx: Context): Type = TypeBoundsOps.hi(self)
  }

}
