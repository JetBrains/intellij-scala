package org.jetbrains.plugins.scala.annotator

class OverrideHighlightingTest extends ScalaHighlightingTestBase {
  import Message._

  def testScl13051(): Unit = {
    val code =
      s"""
         |trait Base {
         |  def foo: Int = 42
         |}
         |
         |class AClass extends Base {
         |  override def foo: String = "42"
         |}
       """.stripMargin
    assertMatches(errorsFromScalaCode(code)) {
      case Error(_, "Overriding type String does not conform to base type Int") :: Nil =>
    }
  }

  def testScl13051_1(): Unit = {
    val code =
      s"""
         |trait T1 {
         |  val foo: T1
         |}
         |trait T2 extends T1 {
         |  override val foo: T2
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, second shape (scala/scala internal.Types LazyType.complete): the
  // overridden member's path-dependent param `Symbol` comes from a SIBLING trait
  // reached via the self type. `complete` is concrete in `Type` and abstract-
  // overridden in `LazyType extends Type`; both params are `SymbolTable.this.Symbol`.
  // scalac accepts it; IntelliJ reported "complete overrides nothing".
  def testSCL21947Complete(): Unit = {
    val code =
      """
        |package internal {
        |  trait Symbols { self: SymbolTable => type Symbol }
        |  trait Types { self: SymbolTable =>
        |    abstract class Type {
        |      def complete(sym: Symbol): Unit = ()
        |    }
        |    abstract class LazyType extends Type {
        |      override def complete(sym: Symbol): Unit
        |    }
        |  }
        |  abstract class SymbolTable extends Symbols with Types
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // An abstract member re-abstracting a CONCRETE inherited member in a nested
  // class. scalac accepts it; IntelliJ reported "f overrides nothing" because the
  // member table keeps the concrete super as the slot's primary node, and plain
  // superSignatures (unlike superSignaturesIncludingSelfType) lacked the by-signature
  // fallback. Not path-dependent — the root of the LazyType.complete report above.
  def testReabstractNested(): Unit = {
    val code =
      """
        |trait Holder {
        |  abstract class A { def f(x: Int): Unit = () }
        |  abstract class B extends A { override def f(x: Int): Unit }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, third shape (scala/scala Infer.inferTypedPattern). The receiver
  // `typer` is the global `object typer extends analyzer.Typer`, reached via the
  // abstract `val global: Global`. Calling `typer.applyTypeToWildcards(pattp)`,
  // IntelliJ computes the param type as seen through that singleton receiver as
  // `global.analyzer.global.analyzer.global.Type` (never collapsing the
  // `analyzer.global: Global.this.type` singleton path back to `global`, and
  // re-applying the rewrite twice), then reports a false type mismatch against the
  // argument `global.Type`. scalac accepts it.
  def testSCL21947Inferencer(): Unit = {
    val code =
      """
        |trait Typers { self: Analyzer =>
        |  import global._
        |  abstract class Typer {
        |    def applyTypeToWildcards(tp: Type): Type = tp
        |  }
        |}
        |trait Infer { self: Analyzer =>
        |  import global._
        |  class Inferencer {
        |    def inferTypedPattern(pattp: Type): Type =
        |      typer.applyTypeToWildcards(pattp)
        |  }
        |}
        |trait Analyzer extends Typers with Infer {
        |  val global: Global
        |}
        |class Global {
        |  type Type
        |  lazy val analyzer = new { val global: Global.this.type = Global.this } with Analyzer
        |  object typer extends analyzer.Typer
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, fourth shape: the singleton val-path `gen.global` (refined to
  // `Global.this.type`) again fails to collapse to `global`, but this time the
  // conformance crosses inheritance: `gen.global.Block <: Tree` (= `global.Tree`)
  // because `Block extends Tree`. The same-member case (`gen.global.Tree`) is fine;
  // the across-inheritance case was a false "type mismatch". scalac accepts it.
  def testSCL21947GenBlock(): Unit = {
    val code =
      """
        |trait Gen { val global: Global }
        |trait Typers { self: Analyzer =>
        |  import global._
        |  def x: Tree = (null: gen.global.Tree)
        |  def y: Tree = (null: gen.global.Block)
        |}
        |trait Analyzer extends Typers { val global: Global }
        |class Global {
        |  class Tree
        |  class Block extends Tree
        |  lazy val gen = new { val global: Global.this.type = Global.this } with Gen
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, fifth shape: like GenBlock, but the Block-typed value comes from a
  // METHOD return (`gen.blk: Block`, as-seen-from `gen`) via an intermediate val,
  // not a written `gen.global.Block`. The asSeenFrom-computed type must still
  // collapse `gen.global` to `global` for `temp <: Tree` to hold. scalac accepts it.
  def testSCL21947GenBlkMethod(): Unit = {
    val code =
      """
        |abstract class TreeGen {
        |  val global: SymbolTable
        |  import global._
        |  def blk: Block = null
        |}
        |abstract class SymbolTable {
        |  class Tree
        |  class Block extends Tree
        |  val gen = new TreeGen { val global: SymbolTable.this.type = SymbolTable.this }
        |}
        |trait Analyzer { val global: SymbolTable }
        |trait Typers { self: Analyzer =>
        |  import global._
        |  val temp = gen.blk
        |  val tree: Tree = temp
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, sixth shape (scala/scala Typers + Global's `override object gen`):
  // `gen` is a `val` in SymbolTable, overridden as an `object` in Global with the
  // early-init `val global: Global.this.type`. Reaching `gen.blk` via `import
  // global._` binds `gen` to the SymbolTable val (whose refinement is relative to
  // SymbolTable), so the prefix `gen.global` does not collapse to `global` and
  // `temp <: Tree` was a false mismatch. scalac accepts it.
  def testSCL21947GenObject(): Unit = {
    val code =
      """
        |trait IGen {
        |  val global: SymbolTable
        |  import global._
        |  def blk: Block = null
        |}
        |trait NscGen extends IGen { val global: Global }
        |class SymbolTable {
        |  class Tree
        |  class Block extends Tree
        |  val gen = new IGen { val global: SymbolTable.this.type = SymbolTable.this }
        |}
        |class Global extends SymbolTable {
        |  override object gen extends { val global: Global.this.type = Global.this } with NscGen
        |}
        |trait Analyzer { val global: Global }
        |trait Typers { self: Analyzer =>
        |  import global._
        |  val temp = gen.blk
        |  val tree: Tree = temp
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, seventh shape (scala/scala nsc ast/TreeGen): the failing conformance
  // sits INSIDE the nsc `TreeGen` itself (which extends `reflect.internal.TreeGen`),
  // so `this` is a TreeGen and `gen` (= `global.gen`, an override-object TreeGen),
  // `Tree`, and `mkAttributedIdent` all come through that TreeGen's `import global._`.
  // `gen.mkAttributedIdent(null): gen.global.RefTree` must conform to substituteThis's
  // `to: Tree` (RefTree extends SymTree extends Tree). IntelliJ reported "Required:
  // Tree, Found: RefTree". scalac accepts it. (The same call in a non-TreeGen context
  // is already green — this context computes the arg type differently.)
  def testSCL21947TreeGen(): Unit = {
    val code =
      """
        |trait Trees { self: SymbolTable =>
        |  abstract class Tree { def substituteThis(clazz: AnyRef, to: Tree): Tree = null }
        |  abstract class SymTree extends Tree
        |  trait NameTree
        |  trait RefTree extends SymTree with NameTree
        |}
        |class SymbolTable extends Trees {
        |  val gen = new IGen { val global: SymbolTable.this.type = SymbolTable.this }
        |}
        |trait IGen {
        |  val global: SymbolTable
        |  import global._
        |  def mkAttributedIdent(sym: AnyRef): RefTree = null
        |}
        |trait NscGen extends IGen {
        |  val global: Global
        |  import global._
        |  def test(tree: Tree): Unit = {
        |    tree.substituteThis(null, gen.mkAttributedIdent(null))
        |  }
        |}
        |class Global extends SymbolTable {
        |  override object gen extends { val global: Global.this.type = Global.this } with NscGen
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, eighth shape (scala/scala nsc BrowsingLoaders.enterIfNew): the
  // path-dependent param `Symbol` is bound through a SINGLETON ALIAS, not a self type.
  // `SymbolLoaders` declares `val symbolTable: SymbolTable` and `import symbolTable._`,
  // so the inherited abstract member's param is `symbolTable.Symbol`. The override (via
  // `import global._`) writes `global.Symbol`. They are the same type only because the
  // intermediate `GlobalSymbolLoaders` aliases `val symbolTable: global.type = global`,
  // which scalac collapses (`symbolTable.Symbol =:= global.Symbol`). scalac accepts it.
  def testSCL21947BrowsingLoaders(): Unit = {
    val code =
      """
        |trait SymbolTable {
        |  type Symbol <: Null
        |}
        |abstract class SymbolLoaders {
        |  val symbolTable: SymbolTable
        |  import symbolTable._
        |  protected def useSymbol(sym: Symbol): Unit
        |}
        |abstract class GlobalSymbolLoaders extends SymbolLoaders {
        |  val global: SymbolTable
        |  val symbolTable: global.type = global
        |}
        |abstract class BrowsingLoaders extends GlobalSymbolLoaders {
        |  val global: SymbolTable
        |  import global._
        |  override protected def useSymbol(sym: Symbol): Unit
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, tenth shape (scala/scala nsc MutableSettings): a refinement placed on
  // the UPPER BOUND of an abstract type member, refining a member (`type T`) that the
  // refined component inherits TRANSITIVELY.
  //
  //   type Setting        <: SettingValue
  //   type BooleanSetting <: Setting { type T = Boolean }
  //
  // `def value: T` lives in `SettingValue` (where `T` is abstract, from
  // `AbsSettingValue`). Selecting `.value` on a `BooleanSetting` must read the bound's
  // `{ type T = Boolean }`, so its type is `BooleanSetting#T` (=:= Boolean) and the
  // assignment to `Boolean` holds. IntelliJ instead left the prefix as the raw
  // `SettingValue.this`, yielding the abstract `SettingValue.this.T` — "Expression of
  // type SettingValue.this.T doesn't conform to expected type Boolean". scalac accepts.
  def testSCL21947MutableSettings(): Unit = {
    val code =
      """
        |abstract class AbsSettings {
        |  class AbsSettingValue { type T }
        |  trait SettingValue extends AbsSettingValue { def value: T }
        |}
        |abstract class MutableSettings extends AbsSettings {
        |  type Setting <: SettingValue
        |  type BooleanSetting  <: Setting {type T = Boolean}
        |  type BooleanSetting1 <: Setting with SettingValue {type T = Boolean}
        |  val viaBound:    Boolean = (??? : BooleanSetting).value
        |  val viaCompound: Boolean = (??? : BooleanSetting1).value
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, eleventh shape (scala/scala nsc ExplicitOuter.OuterPathTransformer):
  // selecting an inner class through an OBJECT member reached on a stable val path.
  //
  //   class C2 extends global.explicitOuter.OuterPathTransformer(null: global.analyzer.Typer)
  //
  // `OuterPathTransformer`'s ctor param is `analyzer.Typer` = `ExplicitOuter.this.global.
  // analyzer.Typer`. As-seen-from the receiver `global.explicitOuter`, that must become
  // `global.explicitOuter.global.analyzer.Typer` and then collapse (`explicitOuter.global
  // =:= global`) to `global.analyzer.Typer` — matching the argument. scalac accepts it.
  def testSCL21947OuterPathTransformer(): Unit = {
    val code =
      """
        |trait Symbols { self: SymbolTable =>
        |  class Symbol
        |}
        |trait Trees { self: SymbolTable =>
        |  abstract class AstTransformer {
        |    def currentClass: Symbol = ???
        |  }
        |}
        |abstract class SymbolTable extends Symbols with Trees
        |trait Typers { self: Analyzer =>
        |  class Typer
        |}
        |trait Analyzer extends Typers {
        |  val global: Global
        |}
        |trait TypingTransformers {
        |  val global: Global
        |  import global._
        |  protected def newRootLocalTyper(unit: CompilationUnit): global.analyzer.Typer = ???
        |  abstract class TypingTransformer(initLocalTyper: global.analyzer.Typer) extends global.AstTransformer {
        |    def this(unit: CompilationUnit) = this(newRootLocalTyper(unit))
        |  }
        |}
        |trait ExplicitOuter extends TypingTransformers {
        |  import global._
        |  abstract class OuterPathTransformer(initLocalTyper: analyzer.Typer) extends TypingTransformer(initLocalTyper)
        |}
        |abstract class Global extends SymbolTable {
        |  class CompilationUnit
        |  lazy val analyzer = new { val global: Global.this.type = Global.this } with Analyzer
        |  object explicitOuter extends { val global: Global.this.type = Global.this } with ExplicitOuter
        |}
        |abstract class SubComponent {
        |  val global: Global
        |}
        |abstract class Repro extends SubComponent {
        |  abstract class C2
        |    extends global.explicitOuter.OuterPathTransformer(null: global.analyzer.Typer)
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947, twelfth shape: abstract type member `type Symbol >: Null` overridden
  // by `class Symbol { def foo = 42 }`. Selecting `.foo` on `currentClass` (whose
  // result type is the abstract `Symbol`) must resolve through the overriding class
  // member, mirroring scalac's `pre.memberType(sym)`.
  def testSCL21947AbstractTypeMemberOverriddenByClass(): Unit = {
    val code =
      """
        |trait Symbols { self: SymbolTable =>
        |  class Symbol { def foo = 42 }
        |}
        |trait ApiUniverse extends ApiTrees {
        |  type Symbol >: Null
        |}
        |trait ApiTrees { self: ApiUniverse =>
        |  abstract class ApiTransformer {
        |    def currentClass: Symbol = ???
        |  }
        |}
        |trait Trees { self: SymbolTable =>
        |  abstract class AstTransformer extends ApiTransformer
        |}
        |abstract class SymbolTable extends Symbols with Trees with ApiUniverse
        |trait Typers { self: Analyzer =>
        |  class Typer
        |}
        |trait Analyzer extends Typers {
        |  val global: Global
        |}
        |trait TypingTransformers {
        |  val global: Global
        |  import global._
        |  protected def newRootLocalTyper(unit: CompilationUnit): global.analyzer.Typer = ???
        |  abstract class TypingTransformer(initLocalTyper: global.analyzer.Typer) extends global.AstTransformer {
        |    def this(unit: CompilationUnit) = this(newRootLocalTyper(unit))
        |  }
        |}
        |trait ExplicitOuter extends TypingTransformers {
        |  import global._
        |  abstract class OuterPathTransformer(initLocalTyper: analyzer.Typer) extends TypingTransformer(initLocalTyper)
        |}
        |abstract class Global extends SymbolTable {
        |  class CompilationUnit
        |  lazy val analyzer = new { val global: Global.this.type = Global.this } with Analyzer
        |  object explicitOuter extends { val global: Global.this.type = Global.this } with ExplicitOuter
        |}
        |abstract class SubComponent {
        |  val global: Global
        |}
        |abstract class Repro extends SubComponent with TypingTransformers {
        |  import global._
        |  abstract class C2
        |    extends global.explicitOuter.OuterPathTransformer(null: global.analyzer.Typer) {
        |    val x: Symbol = currentClass
        |    x.foo
        |    currentClass.foo
        |  }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // SCL-21947: override of a method whose parameter/return types are abstract type
  // members (Symbol, Tree) from ApiUniverse, inherited through the cake pattern.
  // The override check must resolve the super method's signature types through the
  // correct prefix (scalac's asSeenFrom on memberType).
  def testSCL21947OverrideWithAbstractTypeMembers(): Unit = {
    val code =
      """
        |trait Symbols { self: SymbolTable =>
        |  class Symbol { def foo = 42 }
        |}
        |trait ApiUniverse extends ApiTrees {
        |  type Symbol >: Null
        |  type Tree >: Null
        |}
        |trait ApiTrees { self: ApiUniverse =>
        |  abstract class ApiTransformer {
        |    def transformStats(stats: List[Tree], exprOwner: Symbol): List[Tree]
        |  }
        |}
        |trait Trees { self: SymbolTable =>
        |  abstract class AstTransformer extends ApiTransformer {
        |    def m1(a: Symbol): Symbol
        |  }
        |}
        |abstract class SymbolTable extends Symbols with Trees with ApiUniverse
        |trait Typers { self: Analyzer =>
        |  class Typer
        |}
        |trait Analyzer extends Typers {
        |  val global: Global
        |}
        |trait TypingTransformers {
        |  val global: Global
        |  import global._
        |  protected def newRootLocalTyper(unit: CompilationUnit): global.analyzer.Typer = ???
        |  abstract class TypingTransformer(initLocalTyper: global.analyzer.Typer) extends global.AstTransformer {
        |    def this(unit: CompilationUnit) = this(newRootLocalTyper(unit))
        |  }
        |}
        |trait ExplicitOuter extends TypingTransformers {
        |  import global._
        |  abstract class OuterPathTransformer(initLocalTyper: analyzer.Typer) extends TypingTransformer(initLocalTyper)
        |}
        |abstract class Global extends SymbolTable {
        |  class CompilationUnit
        |  lazy val analyzer = new { val global: Global.this.type = Global.this } with Analyzer
        |  object explicitOuter extends { val global: Global.this.type = Global.this } with ExplicitOuter
        |}
        |abstract class SubComponent {
        |  val global: Global
        |}
        |abstract class Repro extends SubComponent with TypingTransformers {
        |  import global._
        |  abstract class C2
        |    extends global.explicitOuter.OuterPathTransformer(null: global.analyzer.Typer) {
        |    override def transformStats(stats: List[Tree], exprOwner: Symbol): List[Tree] = stats
        |  }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // Whittled from the scala/scala reflect cake (internal.Scopes#Scope +
  // runtime.SynchronizedOps#newScope): `class Scope protected[Scopes] ()` is instantiated as
  // `new Scope with SynchronizedScope` from `SynchronizedOps`, which is NOT a subclass of the
  // nested `Scope`. scalac accepts it because `new Scope with SynchronizedScope` defines an
  // anonymous SUBCLASS of `Scope`, and a protected constructor is reachable as the super-ctor
  // call from a subclass. IntelliJ reported a false "No constructor accessible from here".
  def testProtectedCtorAnonClass(): Unit = {
    val code =
      """
        |trait Scopes { self: SymbolTable =>
        |  class Scope protected[Scopes] ()
        |}
        |trait SymbolTable extends Scopes
        |trait T
        |trait SynchronizedOps extends SymbolTable {
        |  trait SynchronizedScope extends Scope
        |  def newScope:   Scope = new Scope() with SynchronizedScope // the original cake shape
        |  def viaMixin:   Scope = new Scope() with T                 // unrelated mixin is enough
        |  def viaBody:    Scope = new Scope() {}                     // body form (already green)
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  // Regression guard for the fix above: a *direct* `new Scope()` (single parent, no body) from a
  // non-subclass is genuinely inaccessible and must still be reported. scalac rejects it too.
  def testProtectedCtorDirectInstantiation(): Unit = {
    val code =
      """
        |trait Scopes { self: SymbolTable =>
        |  class Scope protected[Scopes] ()
        |}
        |trait SymbolTable extends Scopes
        |trait SynchronizedOps extends SymbolTable {
        |  def bad: Scope = new Scope()
        |}
      """.stripMargin
    assertMatches(errorsFromScalaCode(code)) {
      case Error(_, "No constructor accessible from here") :: Nil =>
    }
  }

  def testScl13051_2(): Unit = {
    val code =
      s"""
         |trait Base {
         |  def foo(x: Int): Int = 42
         |  def foo(x: String): String = "42"
         |}
         |class AClass extends Base {
         |  override def foo(x: Int): Int = 42
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051_3(): Unit = {
    val code =
      s"""
         |trait Base[c] {
         |  def foo: c
         |}
         |
         |class AClass[a] extends Base[a]{
         |  override val foo: a = ???
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051Setter(): Unit = {
    val code =
      s"""
         |abstract class A() {
         |  var x: Int
         |}
         |
         |abstract class B() extends A() {
         |  var xx: Int = 0;
         |  def x: Int = xx
         |  def x_=(y: Int) = xx = y;
         |}
       """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL13051replaceDesignators(): Unit = {
    val code =
      """
        |trait Eater {
        |  type Food[T]
        |}
        |
        |trait Fruit {
        |  type Seed
        |}
        |
        |trait PipExtractor {
        |  def extract(a: Fruit): a.Seed
        |}
        |
        |trait LaserGuidedPipExtractor extends PipExtractor {
        |  def extract(f: Fruit): f.Seed
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  private def addType(over: String): String = {
    val split = over.split("=")
    split(0) + ": Int =" + split(1)
  }

  private def inheritWithStringToIntConversion(base: String, over: String) =
    s"""
        |class Test {
        |  implicit def s2i(s: String): Int = s.length
        |  trait Base { $base }
        |  trait Sub extends Base { $over }
        |  trait Sub2 extends Base { ${addType(over)} }
        |}
      """.stripMargin

  def testValInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "val foo = \"\"")))
  }

  def testValInheritReturnTypeParens(): Unit = {
    //TODO: this, for some reason, does not work in compiler
    assertMatches(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "val foo = \"\""))) {
      case Error(_, "Overriding type String does not conform to base type () => Int") :: Nil =>
    }
  }

  def testFunInheritReturnTypeParens(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "def foo = \"\"")))
  }

  def testFunInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "def foo = \"\"")))
  }

  def testParensFunInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "def foo() = \"\"")))
  }

  def testParensFunInheritReturnTypeParens(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "def foo() = \"\"")))
  }

  def testVarInheritReturnType(): Unit = {
    assertNothing(errorsFromScalaCode(inheritWithStringToIntConversion("def foo: Int", "var foo = \"\"")))
  }

  def testVarInheritReturnTypeParens(): Unit = {
    //TODO: this, for some reason, does not work in compiler
    assertMatches(errorsFromScalaCode(inheritWithStringToIntConversion("def foo(): Int", "var foo = \"\""))) {
      case Error(_, "Overriding type String does not conform to base type () => Int") :: Nil =>
    }
  }

  def testSCL14152(): Unit = {
    val code =
      """
        |sealed trait TagExpr
        |
        |sealed trait Composite extends TagExpr {
        |  def head: TagExpr
        |  def tail: Seq[TagExpr]
        |  def all: Seq[TagExpr] = head +: tail
        |}
        |
        |final case class And(head: TagExpr, tail: TagExpr*) extends Composite
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL14922(): Unit = {
    val code =
      """
        |trait A {
        |  trait Internal
        |  val i = new Internal {}
        |}
        |trait B extends A {
        |  trait Internal extends super.Internal
        |  override val i = new Internal {}
        |}
        |trait C extends A {
        |  trait Internal extends super.Internal
        |  override val i = new Internal {}
        |}
        |trait D extends B with C {
        |  trait Internal extends super[B].Internal with super[C].Internal
        |  override val i = new Internal {}
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  def testSCL14707(): Unit = {
    val code =
      """
        |trait BaseComponent {
        |  trait BaseComponent {
        |    val abstractName: String
        |  }
        |}
        |
        |trait AbstractChildComponent extends BaseComponent {
        |  trait AbstractChildComponent extends AbstractChildComponent.super[BaseComponent].BaseComponent {
        |    def abstractMethod() : scala.Unit
        |  }
        |}
        |
        |trait ConcreteComponent extends AbstractChildComponent {
        |
        |  object ConcreteComponent extends AbstractChildComponent {
        |    override def abstractMethod(): Unit = ()
        |
        |    override val abstractName = "hello world"
        |  }
        |}
      """.stripMargin
    assertNothing(errorsFromScalaCode(code))
  }

  protected val SetterAndGetterTraitsCode =
    """trait Setter {
      |  def setValue(foo: String): Unit
      |}
      |
      |trait Getter {
      |  def getValue: String
      |}
      |
      |trait GetterWithSetter extends Getter with Setter
      |""".stripMargin

  // SCL-14462
  def testDontShowErrorForBeanPropertiesOverridingMethods(): Unit = {
    val code =
      s"""$SetterAndGetterTraitsCode
         |
         |import scala.beans.BeanProperty
         |
         |class A1 extends Getter { @BeanProperty var value = "foo" }
         |class B1 extends Setter { @BeanProperty var value = "foo" }
         |class C1 extends GetterWithSetter { @BeanProperty var value = "foo" }
         |
         |class A2(@BeanProperty val value: String) extends Getter
         |class B2(@BeanProperty var value: String) extends Setter
         |class C2(@BeanProperty var value: String) extends GetterWithSetter
         |""".stripMargin
    assertNoErrors(code)
  }

  def testShowErrorForBeanPropertiesOverridingMethodsWithTypeMissmatch(): Unit = {
    val code =
      s"""$SetterAndGetterTraitsCode
         |
         |import scala.beans.BeanProperty
         |
         |class A3 extends Getter { @BeanProperty var value: Int = 42 }
         |class B3 extends Setter { @BeanProperty var value: Int = 42 }
         |class C3 extends GetterWithSetter { @BeanProperty var value: Int = 42 }
         |
         |class A4(@BeanProperty val value: Int) extends Getter
         |class B4(@BeanProperty var value: Int) extends Setter
         |class C4(@BeanProperty var value: Int) extends GetterWithSetter
         |""".stripMargin
    assertErrors(code, Seq(
      Error("value", "Overriding type Int does not conform to base type String"),
      Error("class B3 extends Setter", "Class 'B3' must either be declared abstract or implement abstract member 'setValue(foo: String): Unit' in 'Setter'"),
      Error("class C3 extends GetterWithSetter", "Class 'C3' must either be declared abstract or implement abstract member 'setValue(foo: String): Unit' in 'Setter'"),
      Error("value", "Overriding type Int does not conform to base type String"),
      //
      Error("value", "Overriding type Int does not conform to base type String"),
      Error("class B4(@BeanProperty var value: Int) extends Setter", "Class 'B4' must either be declared abstract or implement abstract member 'setValue(foo: String): Unit' in 'Setter'"),
      Error("class C4(@BeanProperty var value: Int) extends GetterWithSetter", "Class 'C4' must either be declared abstract or implement abstract member 'setValue(foo: String): Unit' in 'Setter'"),
      Error("value", "Overriding type Int does not conform to base type String"),
    )*)
  }
}
