package scala.quoted

import scala.reflect.{ClassTag, TypeTest}

// Reproduces the API of https://github.com/lampepfl/dotty/blob/M2/library/src/scala/quoted/Quotes.scala # Reflection on top of the Scala 2.x ABI.
// Is required because Scala 2.x doesn't support extension methods (and implicit AnyVal classes in a class):
// https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html
class Reflection(val delegate: scala.quoted.Quotes#Reflection) {
  import delegate._

  implicit def typeTestToClassTag[T, U](implicit typeTest: TypeTest[T, U]): ClassTag[U] = new ClassTag[U] {
    override def runtimeClass: Class[_] = throw new UnsupportedOperationException()

    override def unapply(value: Any): Option[U] = typeTest.unapply(value.asInstanceOf[T])
  }

  implicit class TreeOps(self: Tree) {
    def pos: Position = TreeMethodsImpl.pos(self)
    def symbol: Symbol = TreeMethodsImpl.symbol(self)
    def showExtractors: String = TreeMethodsImpl.showExtractors(self)
    def show: String = TreeMethodsImpl.show(self)
    def showAnsiColored: String = TreeMethodsImpl.showAnsiColored(self)
    def isExpr: Boolean = TreeMethodsImpl.isExpr(self)
    def asExpr: Expr[Any] = TreeMethodsImpl.asExpr(self)
    def asExprOfp[T](implicit t: Type[T]): Expr[T] = TreeMethodsImpl.asExprOf[T](self)
  }
  implicit class PackageClauseOps(self: PackageClause) {
    def pid: Ref = PackageClauseMethodsImpl.pid(self)
    def stats: List[Tree] = PackageClauseMethodsImpl.stats(self)
  }
  implicit class ImportOps(self: Import)  {
    def expr: Term = ImportMethodsImpl.expr(self)
    def selectors: List[ImportSelector] = ImportMethodsImpl.selectors(self)
  }
  implicit class DefinitionOps(self: Definition) {
    def name: String = DefinitionMethodsImpl.name(self)
  }
  implicit class ClassDefOps(self: ClassDef) {
    def constructor: DefDef = ClassDefMethodsImpl.constructor(self)
    def parents: List[Tree ] = ClassDefMethodsImpl.parents(self)
    def derived: List[TypeTree] = ClassDefMethodsImpl.derived(self)
    def self: Option[ValDef] = ClassDefMethodsImpl.self(self)
    def body: List[Statement] = ClassDefMethodsImpl.body(self)
  }
  implicit class DefDefOps(self: DefDef) {
    def typeParams: List[TypeDef] = DefDefMethodsImpl.typeParams(self)
    def paramss: List[List[ValDef]] = DefDefMethodsImpl.paramss(self)
    def returnTpt: TypeTree = DefDefMethodsImpl.returnTpt(self)
    def rhs: Option[Term] = DefDefMethodsImpl.rhs(self)
  }
  implicit class ValDefOps(self: ValDef) {
    def tpt: TypeTree = ValDefMethodsImpl.tpt(self)
    def rhs: Option[Term] = ValDefMethodsImpl.rhs(self)
  }
  implicit class TypeDefOps(self: TypeDef) {
    def rhs: Tree = TypeDefMethodsImpl.rhs(self)
  }
  implicit class TermOps(self: Term) {
    def seal: scala.quoted.Expr[Any] = TermMethodsImpl.seal(self)
    def sealOpt: Option[scala.quoted.Expr[Any]] = TermMethodsImpl.sealOpt(self)
    def tpe: TypeRepr = TermMethodsImpl.tpe(self)
    def underlyingArgument: Term = TermMethodsImpl.underlyingArgument(self)
    def underlying: Term = TermMethodsImpl.underlying(self)
    def etaExpand(symbol: Symbol): Term = TermMethodsImpl.etaExpand(self, symbol)
    def appliedTo(arg: Term): Term = TermMethodsImpl.appliedTo(self, arg)
    def appliedTo(arg: Term, args: Term*): Term = TermMethodsImpl.appliedTo(self, arg, args: _*)
    def appliedToArgs(args: List[Term]): Apply = TermMethodsImpl.appliedToArgs(self, args)
    def appliedToArgss(argss: List[List[Term]]): Term = TermMethodsImpl.appliedToArgss(self, argss)
    def appliedToNone: Apply = TermMethodsImpl.appliedToNone(self)
    def appliedToType(targ: TypeRepr): Term = TermMethodsImpl.appliedToType(self, targ)
    def appliedToTypes(targs: List[TypeRepr]): Term = TermMethodsImpl.appliedToTypes(self, targs)
    def appliedToTypeTrees(targs: List[TypeTree]): Term = TermMethodsImpl.appliedToTypeTrees(self)(targs)
    def select(sym: Symbol): Select = TermMethodsImpl.select(self)(sym)
  }
  implicit class IdentOps(self: Ident) {
    def name: String = IdentMethodsImpl.name(self)
  }
  implicit class SelectOps(self: Select) {
    def qualifier: Term = SelectMethodsImpl.qualifier(self)
    def name: String = SelectMethodsImpl.name(self)
    def signature: Option[Signature] = SelectMethodsImpl.signature(self)
  }
  implicit class LiteralOps(self: Literal) {
    def constant: Constant = LiteralMethodsImpl.constant(self)
  }
  implicit class ThisOps(self: This) {
    def id: Option[String] = ThisMethodsImpl.id(self)
  }
  implicit class NewOps(self: New) {
    def tpt: TypeTree = NewMethodsImpl.tpt(self)
  }
  implicit class NamedArgOps(self: NamedArg) {
    def name: String = NamedArgMethodsImpl.name(self)
    def value: Term = NamedArgMethodsImpl.value(self)
  }
  implicit class ApplyOps(self: Apply) {
    def fun: Term = ApplyMethodsImpl.fun(self)
    def args: List[Term] = ApplyMethodsImpl.args(self)
  }
  implicit class TypeApplyOps(self: TypeApply) {
    def fun: Term = TypeApplyMethodsImpl.fun(self)
    def args: List[TypeTree] = TypeApplyMethodsImpl.args(self)
  }
  implicit class SuperOps(self: Super) {
    def qualifier: Term = SuperMethodsImpl.qualifier(self)
    def id: Option[String] = SuperMethodsImpl.id(self)
    def idPos: Position = SuperMethodsImpl.idPos(self)
  }
  implicit class TypedOps(self: Typed) {
    def expr: Term = TypedMethodsImpl.expr(self)
    def tpt: TypeTree = TypedMethodsImpl.tpt(self)
  }
  implicit class AssignOps(self: Assign) {
    def lhs: Term = AssignMethodsImpl.lhs(self)
    def rhs: Term = AssignMethodsImpl.rhs(self)
  }
  implicit class BlockOps(self: Block) {
    def statements: List[Statement] = BlockMethodsImpl.statements(self)
    def expr: Term = BlockMethodsImpl.expr(self)
  }
  implicit class ClosureOps(self: Closure) {
    def meth: Term = ClosureMethodsImpl.meth(self)
    def tpeOpt: Option[TypeRepr] = ClosureMethodsImpl.tpeOpt(self)
  }
  implicit class IfOps(self: If) {
    def cond: Term = IfMethodsImpl.cond(self)
    def thenp: Term = IfMethodsImpl.thenp(self)
    def elsep: Term = IfMethodsImpl.elsep(self)
  }
  implicit class MatchOps(self: Match) {
    def scrutinee: Term = MatchMethodsImpl.scrutinee(self)
    def cases: List[CaseDef] = MatchMethodsImpl.cases(self)
  }
  implicit class SummonFromOps(self: SummonFrom) {
    def cases: List[CaseDef] = SummonFromMethodsImpl.cases(self)
  }
  implicit class TryOps(self: Try) {
    def body: Term = TryMethodsImpl.body(self)
    def cases: List[CaseDef] = TryMethodsImpl.cases(self)
    def finalizer: Option[Term] = TryMethodsImpl.finalizer(self)
  }
  implicit class ReturnOps(self: Return) {
    def expr: Term = ReturnMethodsImpl.expr(self)
    def from: Symbol = ReturnMethodsImpl.from(self)
  }
  implicit class RepeatedOps(self: Repeated) {
    def elems: List[Term] = RepeatedMethodsImpl.elems(self)
    def elemtpt: TypeTree = RepeatedMethodsImpl.elemtpt(self)
  }
  implicit class InlinedOps(self: Inlined) {
    def call: Option[Tree ] = InlinedMethodsImpl.call(self)
    def bindings: List[Definition] = InlinedMethodsImpl.bindings(self)
    def body: Term = InlinedMethodsImpl.body(self)
  }
  implicit class SelectOuterOps(self: SelectOuter) {
    def qualifier: Term = SelectOuterMethodsImpl.qualifier(self)
    def name: String = SelectOuterMethodsImpl.name(self)
    def level: Int = SelectOuterMethodsImpl.level(self)
  }
  implicit class WhileOps(self: While) {
    def cond: Term = WhileMethodsImpl.cond(self)
    def body: Term = WhileMethodsImpl.body(self)
  }
  implicit class TypeTreeOps(self: TypeTree) {
    def tpe: TypeRepr = TypeTreeMethodsImpl.tpe(self)
  }
  implicit class TypeIdentOps(self: TypeIdent) {
    def name: String = TypeIdentMethodsImpl.name(self)
  }
  implicit class TypeSelectOps(self: TypeSelect) {
    def qualifier: Term = TypeSelectMethodsImpl.qualifier(self)
    def name: String = TypeSelectMethodsImpl.name(self)
  }
  implicit class TypeProjectionOps(self: TypeProjection) {
    def qualifier: TypeTree = TypeProjectionMethodsImpl.qualifier(self)
    def name: String = TypeProjectionMethodsImpl.name(self)
  }
  implicit class SingletonOps(self: Singleton) {
    def ref: Term = SingletonMethodsImpl.ref(self)
  }
  implicit class RefinedOps(self: Refined) {
    def tpt: TypeTree = RefinedMethodsImpl.tpt(self)
    def refinements: List[Definition] = RefinedMethodsImpl.refinements(self)
  }
  implicit class AppliedOps(self: Applied) {
    def tpt: TypeTree = AppliedMethodsImpl.tpt(self)
    def args: List[Tree ] = AppliedMethodsImpl.args(self)
  }
  implicit class AnnotatedOps(self: Annotated) {
    def arg: TypeTree = AnnotatedMethodsImpl.arg(self)
    def annotation: Term = AnnotatedMethodsImpl.annotation(self)
  }
  implicit class MatchTypeTreeOps(self: MatchTypeTree) {
    def bound: Option[TypeTree] = MatchTypeTreeMethodsImpl.bound(self)
    def selector: TypeTree = MatchTypeTreeMethodsImpl.selector(self)
    def cases: List[TypeCaseDef] = MatchTypeTreeMethodsImpl.cases(self)
  }
  implicit class ByNameOps(self: ByName) {
    def result: TypeTree = ByNameMethodsImpl.result(self)
  }
  implicit class LambdaTypeTreeOps(self: LambdaTypeTree) {
    def tparams: List[TypeDef] = LambdaTypeTreeMethodsImpl.tparams(self)
    def body: Tree = LambdaTypeTreeMethodsImpl.body(self)
  }
  implicit class TypeBindOps(self: TypeBind) {
    def name: String = TypeBindMethodsImpl.name(self)
    def body: Tree = TypeBindMethodsImpl.body(self)
  }
  implicit class TypeBlockOps(self: TypeBlock) {
    def aliases: List[TypeDef] = TypeBlockMethodsImpl.aliases(self)
    def tpt: TypeTree = TypeBlockMethodsImpl.tpt(self)
  }
  implicit class TypeBoundsTreeOps(self: TypeBoundsTree) {
    def tpe: TypeBounds = TypeBoundsTreeMethodsImpl.tpe(self)
    def low: TypeTree = TypeBoundsTreeMethodsImpl.low(self)
    def hi: TypeTree = TypeBoundsTreeMethodsImpl.hi(self)
  }
  implicit class WildcardTypeTreeOps(self: WildcardTypeTree) {
    def tpe: TypeRepr = WildcardTypeTreeMethodsImpl.tpe(self)
  }
  implicit class CaseDefOps(self: CaseDef) {
    def pattern: Tree = CaseDefMethodsImpl.pattern(self)
    def guard: Option[Term] = CaseDefMethodsImpl.guard(self)
    def rhs: Term = CaseDefMethodsImpl.rhs(self)
  }
  implicit class TypeCaseDefOps(self: TypeCaseDef) {
    def pattern: TypeTree = TypeCaseDefMethodsImpl.pattern(self)
    def rhs: TypeTree = TypeCaseDefMethodsImpl.rhs(self)
  }
  implicit class BindOps(self: Bind) {
    def name: String = BindMethodsImpl.name(self)
    def pattern: Tree = BindMethodsImpl.pattern(self)
  }
  implicit class UnapplyOps(self: Unapply) {
    def fun: Term = UnapplyMethodsImpl.fun(self)
    def implicits: List[Term] = UnapplyMethodsImpl.implicits(self)
    def patterns: List[Tree] = UnapplyMethodsImpl.patterns(self)
  }
  implicit class AlternativesOps(self: Alternatives) {
    def patterns: List[Tree] = AlternativesMethodsImpl.patterns(self)
  }

  implicit class SimpleSelectorOps(self: SimpleSelector) {
    def name: String = SimpleSelectorMethodsImpl.name(self)
    def namePos: Position = SimpleSelectorMethodsImpl.namePos(self)
  }
  implicit class RenameSelectorOps(self: RenameSelector) {
    def fromName: String = RenameSelectorMethodsImpl.fromName(self)
    def fromPos: Position = RenameSelectorMethodsImpl.fromPos(self)
    def toName: String = RenameSelectorMethodsImpl.toName(self)
    def toPos: Position = RenameSelectorMethodsImpl.toPos(self)
  }
  implicit class OmitSelectorOps(self: OmitSelector) {
    def name: String = OmitSelectorMethodsImpl.name(self)
    def namePos: Position = OmitSelectorMethodsImpl.namePos(self)
  }

  implicit class TypeOps(self: TypeRepr) {
    def showExtractors: String = TypeReprMethodsImpl.showExtractors(self)
    def show: String = TypeReprMethodsImpl.show(self)
    def showAnsiColored: String = TypeReprMethodsImpl.showAnsiColored(self)
    def asType: Type[_] = TypeReprMethodsImpl.asType(self)
    def =:=(that: TypeRepr): Boolean = TypeReprMethodsImpl.=:=(self)(that)
    def <:<(that: TypeRepr): Boolean = TypeReprMethodsImpl.<:<(self)(that)
    def widen: TypeRepr = TypeReprMethodsImpl.widen(self)
    def widenTermRefExpr: TypeRepr = TypeReprMethodsImpl.widenTermRefExpr(self)
    def dealias: TypeRepr = TypeReprMethodsImpl.dealias(self)
    def simplified: TypeRepr = TypeReprMethodsImpl.simplified(self)
    def classSymbol: Option[Symbol] = TypeReprMethodsImpl.classSymbol(self)
    def typeSymbol: Symbol = TypeReprMethodsImpl.typeSymbol(self)
    def termSymbol: Symbol = TypeReprMethodsImpl.termSymbol(self)
    def isSingleton: Boolean = TypeReprMethodsImpl.isSingleton(self)
    def memberType(member: Symbol): TypeRepr = TypeReprMethodsImpl.memberType(self)(member)
    def baseClasses: List[Symbol] = TypeReprMethodsImpl.baseClasses(self)
    def baseType(member: Symbol): TypeRepr = TypeReprMethodsImpl.baseType(self)(member)
    def derivesFrom(cls: Symbol): Boolean = TypeReprMethodsImpl.derivesFrom(self)(cls)
    def isFunctionType: Boolean = TypeReprMethodsImpl.isFunctionType(self)
    def isContextFunctionType: Boolean = TypeReprMethodsImpl.isContextFunctionType(self)
    def isErasedFunctionType: Boolean = TypeReprMethodsImpl.isErasedFunctionType(self)
    def isDependentFunctionType: Boolean = TypeReprMethodsImpl.isDependentFunctionType(self)
    def select(sym: Symbol): TypeRepr = TypeReprMethodsImpl.select(self)(sym)
    def appliedTo(targ: TypeRepr): TypeRepr = TypeReprMethodsImpl.appliedTo(self, targ)
    def appliedTo(targs: List[TypeRepr]): TypeRepr = TypeReprMethodsImpl.appliedTo(self, targs)
  }
  implicit class ConstantTypeOps(self: ConstantType) {
    def constant: Constant = ConstantTypeMethodsImpl.constant(self)
  }
  implicit class TermRefOps(self: TermRef) {
    def qualifier: TypeRepr = TermRefMethodsImpl.qualifier(self)
    def name: String = TermRefMethodsImpl.name(self)
  }
  implicit class TypeRefOps(self: TypeRef) {
    def qualifier: TypeRepr = TypeRefMethodsImpl.qualifier(self)
    def name: String = TypeRefMethodsImpl.name(self)
    def isOpaqueAlias: Boolean = TypeRefMethodsImpl.isOpaqueAlias(self)
    def translucentSuperType: TypeRepr = TypeRefMethodsImpl.translucentSuperType(self)
  }
  implicit class SuperTypeOps(self: SuperType) {
    def thistpe: TypeRepr = SuperTypeMethodsImpl.thistpe(self)
    def supertpe: TypeRepr = SuperTypeMethodsImpl.supertpe(self)
  }
  implicit class RefinementOps(self: Refinement) {
    def parent: TypeRepr = RefinementMethodsImpl.parent(self)
    def name: String = RefinementMethodsImpl.name(self)
    def info: TypeRepr = RefinementMethodsImpl.info(self)
  }
  implicit class AppliedTypeOps(self: AppliedType) {
    def tycon: TypeRepr = AppliedTypeMethodsImpl.tycon(self)
    def args: List[TypeRepr] = AppliedTypeMethodsImpl.args(self)
  }
  implicit class AnnotatedTypeOps(self: AnnotatedType) {
    def underlying: TypeRepr = AnnotatedTypeMethodsImpl.underlying(self)
    def annot: Term = AnnotatedTypeMethodsImpl.annot(self)
  }
  implicit class AndTypeOps(self: AndType) {
    def left: TypeRepr = AndTypeMethodsImpl.left(self)
    def right: TypeRepr = AndTypeMethodsImpl.right(self)
  }
  implicit class OrTypeOps(self: OrType) {
    def left: TypeRepr = OrTypeMethodsImpl.left(self)
    def right: TypeRepr = OrTypeMethodsImpl.right(self)
  }
  implicit class MatchTypeOps(self: MatchType) {
    def bound: TypeRepr = MatchTypeMethodsImpl.bound(self)
    def scrutinee: TypeRepr = MatchTypeMethodsImpl.scrutinee(self)
    def cases: List[TypeRepr] = MatchTypeMethodsImpl.cases(self)
  }
  implicit class ByNameTypeOps(self: ByNameType) {
    def underlying: TypeRepr = ByNameTypeMethodsImpl.underlying(self)
  }
  implicit class ParamRefOps(self: ParamRef) {
    def binder: LambdaType = ParamRefMethodsImpl.binder(self)
    def paramNum: Int = ParamRefMethodsImpl.paramNum(self)
  }
  implicit class ThisTypeOps(self: ThisType) {
    def tref: TypeRepr = ThisTypeMethodsImpl.tref(self)
  }
  implicit class RecursiveThisOps(self: RecursiveThis) {
    def binder: RecursiveType = RecursiveThisMethodsImpl.binder(self)
  }
  implicit class RecursiveTypeOps(self: RecursiveType) {
    def underlying: TypeRepr = RecursiveTypeMethodsImpl.underlying(self)
    def recThis: RecursiveThis = RecursiveTypeMethodsImpl.recThis(self)
  }
  implicit class MethodTypeOps(self: MethodType) {
    def isImplicit: Boolean = MethodTypeMethodsImpl.isImplicit(self)
    def isErased: Boolean = MethodTypeMethodsImpl.isErased(self)
    def param(idx: Int): TypeRepr = MethodTypeMethodsImpl.param(self)(idx)
    def paramNames: List[String] = MethodTypeMethodsImpl.paramNames(self)
    def paramTypes: List[TypeRepr] = MethodTypeMethodsImpl.paramTypes(self)
    def resType: TypeRepr = MethodTypeMethodsImpl.resType(self)
  }
  implicit class PolyTypeOps(self: PolyType) {
    def param(self: PolyType)(idx: Int): TypeRepr = PolyTypeMethodsImpl.param(self)(idx)
    def paramNames: List[String] = PolyTypeMethodsImpl.paramNames(self)
    def paramBounds: List[TypeBounds] = PolyTypeMethodsImpl.paramBounds(self)
    def resType: TypeRepr = PolyTypeMethodsImpl.resType(self)
  }
  implicit class TypeLambdaOps(self: TypeLambda) {
    def paramNames: List[String] = TypeLambdaMethodsImpl.paramNames(self)
    def paramBounds: List[TypeBounds] = TypeLambdaMethodsImpl.paramBounds(self)
    def param(idx: Int) : TypeRepr = TypeLambdaMethodsImpl.param(self)(idx)
    def resType: TypeRepr = TypeLambdaMethodsImpl.resType(self)
  }
  implicit class TypeBoundsOps(self: TypeBounds) {
    def low: TypeRepr = TypeBoundsMethodsImpl.low(self)
    def hi: TypeRepr = TypeBoundsMethodsImpl.hi(self)
  }

  implicit class ConstantOps(self: Constant) {
    def value: Any = ConstantMethodsImpl.value(self)
    def showExtractors: String = ConstantMethodsImpl.showExtractors(self)
    def show: String = ConstantMethodsImpl.show(self)
    def showAnsiColored: String = ConstantMethodsImpl.showAnsiColored(self)
  }

  implicit class ImplicitSearchSuccessOps(self: ImplicitSearchSuccess) {
    def tree: Term = ImplicitSearchSuccessMethodsImpl.tree(self)
  }
  implicit class ImplicitSearchFailureOps(self: ImplicitSearchFailure) {
    def explanation: String = ImplicitSearchFailureMethodsImpl.explanation(self)
  }

  implicit class SymbolOps(self: Symbol) {
    def owner: Symbol = SymbolMethodsImpl.owner(self)
    def maybeOwner: Symbol = SymbolMethodsImpl.maybeOwner(self)
    def flags: Flags = SymbolMethodsImpl.flags(self)
    def privateWithin: Option[TypeRepr] = SymbolMethodsImpl.privateWithin(self)
    def protectedWithin: Option[TypeRepr] = SymbolMethodsImpl.protectedWithin(self)
    def name: String = SymbolMethodsImpl.name(self)
    def fullName: String = SymbolMethodsImpl.fullName(self)
    def pos: Position = SymbolMethodsImpl.pos(self)
    def documentation: Option[Documentation] = SymbolMethodsImpl.documentation(self)
    def tree: Tree = SymbolMethodsImpl.tree(self)
    def annots: List[Term] = SymbolMethodsImpl.annots(self)
    def isDefinedInCurrentRun: Boolean = SymbolMethodsImpl.isDefinedInCurrentRun(self)
    def isLocalDummy: Boolean = SymbolMethodsImpl.isLocalDummy(self)
    def isRefinementClass: Boolean = SymbolMethodsImpl.isRefinementClass(self)
    def isAliasType: Boolean = SymbolMethodsImpl.isAbstractType(self)
    def isAnonymousClass: Boolean = SymbolMethodsImpl.isAnonymousClass(self)
    def isAnonymousFunction: Boolean = SymbolMethodsImpl.isAnonymousFunction(self)
    def isAbstractType: Boolean = SymbolMethodsImpl.isAbstractType(self)
    def isClassConstructor: Boolean = SymbolMethodsImpl.isClassConstructor(self)
    def isType: Boolean = SymbolMethodsImpl.isType(self)
    def isTerm: Boolean = SymbolMethodsImpl.isTerm(self)
    def isPackageDef: Boolean = SymbolMethodsImpl.isPackageDef(self)
    def isClassDef: Boolean = SymbolMethodsImpl.isClassDef(self)
    def isTypeDef: Boolean = SymbolMethodsImpl.isTypeDef(self)
    def isValDef: Boolean = SymbolMethodsImpl.isValDef(self)
    def isDefDef: Boolean = SymbolMethodsImpl.isDefDef(self)
    def isBind: Boolean = SymbolMethodsImpl.isBind(self)
    def isNoSymbol: Boolean = SymbolMethodsImpl.isNoSymbol(self)
    def exists: Boolean = SymbolMethodsImpl.exists(self)
    def fields: List[Symbol] = SymbolMethodsImpl.fields(self)
    def field(name: String): Symbol = SymbolMethodsImpl.field(self)(name)
    def classMethod(name: String): List[Symbol] = SymbolMethodsImpl.classMethod(self)(name)
    def classMethods: List[Symbol] = SymbolMethodsImpl.classMethods(self)
    def method(name: String): List[Symbol] = SymbolMethodsImpl.method(self)(name)
    def methods: List[Symbol] = SymbolMethodsImpl.methods(self)
    def caseFields: List[Symbol] = SymbolMethodsImpl.caseFields(self)
    def isTypeParam: Boolean = SymbolMethodsImpl.isTypeParam(self)
    def signature: Signature = SymbolMethodsImpl.signature(self)
    def moduleClass: Symbol = SymbolMethodsImpl.moduleClass(self)
    def companionClass: Symbol = SymbolMethodsImpl.companionClass(self)
    def companionModule: Symbol = SymbolMethodsImpl.companionModule(self)
    def showExtractors: String = SymbolMethodsImpl.showExtractors(self)
    def show: String = SymbolMethodsImpl.show(self)
    def showAnsiColored: String = SymbolMethodsImpl.showAnsiColored(self)
    def children: List[Symbol] = SymbolMethodsImpl.children(self)
  }

  implicit class SignatureOps(self: Signature) {
    def paramSigs: List[Any] = SignatureMethodsImpl.paramSigs(self)
    def resultSig: String = SignatureMethodsImpl.resultSig(self)
  }

  implicit class FlagsOps(self: Flags) {
    def is(that: Flags): Boolean = FlagsMethodsImpl.is(self)(that)
    def |(that: Flags): Flags = FlagsMethodsImpl.|(self)(that)
    def &(that: Flags): Flags = FlagsMethodsImpl.&(self)(that)
    def showExtractors: String = FlagsMethodsImpl.showExtractors(self)
    def show: String = FlagsMethodsImpl.show(self)
    def showAnsiColored: String = FlagsMethodsImpl.showAnsiColored(self)
  }

  implicit class PositionOps(self: Position) {
    def start: Int = PositionMethodsImpl.start(self)
    def end: Int = PositionMethodsImpl.end(self)
    def exists: Boolean = PositionMethodsImpl.exists(self)
    def sourceFile: SourceFile = PositionMethodsImpl.sourceFile(self)
    def startLine: Int = PositionMethodsImpl.startLine(self)
    def endLine: Int = PositionMethodsImpl.endLine(self)
    def startColumn: Int = PositionMethodsImpl.startColumn(self)
    def endColumn: Int = PositionMethodsImpl.endColumn(self)
    def sourceCode: String = PositionMethodsImpl.sourceCode(self)
  }

  implicit class SourceFileOps(self: SourceFile) {
    def jpath: java.nio.file.Path = SourceFileMethodsImpl.jpath(self)
    def content: String = SourceFileMethodsImpl.content(self)
  }

  implicit class DocumentationOps(self: Documentation) {
    def raw: String = DocumentationMethodsImpl.raw(self)
    def expanded: Option[String] = DocumentationMethodsImpl.expanded(self)
    def usecases: List[(String, Option[DefDef])] = DocumentationMethodsImpl.usecases(self)
  }
}
