package org.jetbrains.plugins.scala.annotator

// Distilled from the false errors IntelliJ reports in scala/scala's Typers.scala
// (SCL-21947). Each case compiles with scalac 2.13.
class CakeTypersReproTest extends ScalaHighlightingTestBase {

  // reflect.api / reflect.internal / nsc layering, as in scala/scala: api declares
  // abstract types, internal realizes them with classes under `self: SymbolTable`,
  // nsc's Global extends SymbolTable, and Typers sees everything via `import global._`.
  private val Cake =
    """
      |package api {
      |  trait Names { self: Universe => type Name >: Null <: AnyRef; type TermName >: Null <: Name }
      |  trait Symbols { self: Universe => type Symbol >: Null <: AnyRef; type ClassSymbol >: Null <: Symbol }
      |  trait Types { self: Universe => type Type >: Null <: AnyRef }
      |  trait Trees { self: Universe => type Tree >: Null <: AnyRef }
      |  abstract class Universe extends Names with Symbols with Types with Trees
      |}
      |package reflect {
      |  trait Names extends api.Names { self: SymbolTable =>
      |    abstract class Name
      |    abstract class TermName extends Name
      |  }
      |  trait StdNames { self: SymbolTable =>
      |    object nme { val ERROR: TermName = null }
      |  }
      |  trait Symbols extends api.Symbols { self: SymbolTable =>
      |    abstract class Symbol extends Attachable {
      |      def name: TermName = null
      |      def setInfo(i: Int): this.type = this
      |      def tpeHK: Type = null
      |      def newAbstractType(n: String): AbstractTypeSymbol = null
      |    }
      |    abstract class AbstractTypeSymbol extends Symbol
      |    abstract class ClassSymbol extends Symbol
      |  }
      |  trait Definitions { self: SymbolTable =>
      |    object definitions { def FunctionClass(i: Int): ClassSymbol = null }
      |  }
      |  trait StdAttachments { self: SymbolTable =>
      |    trait Attachable { def setPos(p: Int): this.type = this }
      |  }
      |  trait Types extends api.Types { self: SymbolTable =>
      |    abstract class Type { def baseType(s: Symbol): Type = this }
      |  }
      |  trait Trees extends api.Trees { self: SymbolTable =>
      |    abstract class TreeContextApiImpl { this: Tree =>
      |      def setSymbol(s: Symbol): this.type = this
      |    }
      |    abstract class Tree extends TreeContextApiImpl with Attachable {
      |      def setType(t: Type): this.type = this
      |    }
      |    abstract class TypTree extends Tree
      |    case class Ident(n: String) extends Tree
      |    object EmptyTree extends Tree
      |  }
      |  abstract class SymbolTable extends api.Universe
      |    with Names with StdNames with Symbols with Definitions with StdAttachments with Types with Trees
      |}
      |package nsc {
      |  trait Trees extends reflect.Trees { self: Global =>
      |    case class DocDef(c: String, d: Tree) extends Tree
      |    case class Deferred(pre: Tree)(val check: () => Tree) extends TypTree
      |  }
      |  class Global extends reflect.SymbolTable with Trees
      |  trait Analyzer extends Typers { val global: Global }
      |  trait Typers { self: Analyzer =>
      |    import global._
      |""".stripMargin

  private def inTypers(body: String): String = Cake + body + "\n  }\n}\n"

  private def assertClean(body: String): Unit =
    assertNothing(errorsFromScalaCode(inTypers(body)))

  // --- members of an object nested in a cake trait, reached through `global` ---

  // Typers.scala:4265 shape (`nme.ERROR` vs `sym.name`).
  def testNestedObjectValAsName(): Unit =
    assertClean("    def f: Name = nme.ERROR")

  def testNestedObjectValLub(): Unit =
    assertClean("    def f(s: Symbol, c: Boolean): (Name, Int) = if (c) (nme.ERROR, 1) else (s.name, 2)")

  // Typers.scala:3106 / 3564 shape (`definitions.FunctionClass(n)`).
  def testNestedObjectDefAsSymbol(): Unit =
    assertClean("    def f: Symbol = definitions.FunctionClass(1)")

  def testNestedObjectDefAsArg(): Unit =
    assertClean("    def f(t: Type): Type = t.baseType(definitions.FunctionClass(1))")

  def testNestedObjectTreeAsTree(): Unit =
    assertClean("    def f: Tree = EmptyTree")

  // --- this.type results ---

  // Typers.scala:5635 shape.
  def testThisTypeResultChained(): Unit =
    assertClean("    def f(t: Ident, tp: Type): Tree = t.setType(tp).setPos(1)")

  def testThisTypeOnTwoParamListCaseClass(): Unit =
    assertClean("    def f(t: Tree, tp: Type): Tree = Deferred(t)(() => t).setType(tp).setPos(1)")

  // this.type declared in a class with a self type (`TreeContextApiImpl { this: Tree => }`).
  def testThisTypeFromSelfTypedClass(): Unit =
    assertClean("    def f(t: Ident, s: Symbol): Tree = t.setSymbol(s)")

  // Typers.scala:1986 shape: a case class from the nsc layer, Attachable#setPos.
  def testThisTypeInMap(): Unit =
    assertClean("    def f(ts: List[Tree]): List[Tree] = ts map (t => DocDef(\"c\", t) setPos 1)")

  // Typers.scala:4804 shape.
  def testLubWithThisTypeResult(): Unit =
    assertClean(
      """    def f(t: Tree, owner: Symbol, c: Boolean): Tree = {
        |      val sym = if (c) owner else owner.newAbstractType("T") setInfo 1
        |      t setSymbol sym setType sym.tpeHK
        |    }""".stripMargin)

  // --- an abstract type member's bound seen through the self type ---

  // `nme.ERROR: NameType`, with `NameType = TermName` declared in `StdNames` but `TermName` and
  // `Name` in the sibling `Names`, seen through `StdNames`' self type. (`definitions.FunctionClass`
  // through the self type is covered by OverrideHighlightingTest.testInferredLubThroughSelfType*.)
  private val SelfTypedSibling =
    """
      |package p {
      |  trait StdNames { self: SymbolTable =>
      |    abstract class CommonNames { type NameType >: Null <: Name }
      |    abstract class TermNames extends CommonNames { override type NameType = TermName }
      |    object nme extends TermNames { val ERROR: NameType = null }
      |  }
      |  trait Names { self: SymbolTable =>
      |    abstract class Name
      |    abstract class TermName extends Name
      |  }
      |  abstract class SymbolTable extends StdNames with Names
      |  class Global extends SymbolTable
      |  trait Analyzer extends Typers { val global: Global }
      |  trait Typers { self: Analyzer =>
      |    import global._
      |""".stripMargin

  private def assertCleanSelfTyped(body: String): Unit =
    assertNothing(errorsFromScalaCode(SelfTypedSibling + body + "\n  }\n}\n"))

  def testAbstractTypeBoundThroughSelfTypeInLub(): Unit =
    assertCleanSelfTyped("    def f(n: TermName, c: Boolean): (Name, Int) = if (c) (nme.ERROR, 1) else (n, 2)")

  // --- lubs over cake classes seen from `global` ---
  //
  // The lubs themselves (Typers.scala:4804 / 4265: `if (c) owner else owner.newAbstractType(..)`,
  // `if (c) nme.ERROR else sym.name`) still type as the declaring trait's `Symbols.this.Symbol` /
  // `Names.this.Name` rather than `global.Symbol` / `global.Name`; those cases are not here yet.

  private val LubCake =
    """
      |package p {
      |  trait Names { self: SymbolTable =>
      |    abstract class Name
      |    abstract class TermName extends Name
      |    abstract class TypeName extends Name
      |  }
      |  trait StdNames { self: SymbolTable =>
      |    abstract class CommonNames { type NameType >: Null <: Name }
      |    abstract class TermNames extends CommonNames { override type NameType = TermName }
      |    object nme extends TermNames { val ERROR: NameType = null }
      |  }
      |  trait Symbols { self: SymbolTable =>
      |    abstract class Symbol {
      |      type NameType >: Null <: Name
      |      def name: NameType = null
      |      def setInfo(i: Int): this.type = this
      |      def newAliasType(n: String): AliasTypeSymbol = null
      |      def newAbstractType(n: String): AbstractTypeSymbol = null
      |    }
      |    abstract class TypeSymbol extends Symbol
      |    abstract class AliasTypeSymbol extends TypeSymbol
      |    abstract class AbstractTypeSymbol extends TypeSymbol
      |  }
      |  abstract class SymbolTable extends Names with StdNames with Symbols
      |  class Global extends SymbolTable
      |  trait Analyzer extends Typers { val global: Global }
      |  trait Typers { self: Analyzer =>
      |    import global._
      |""".stripMargin

  private def assertCleanLub(body: String): Unit =
    assertNothing(errorsFromScalaCode(LubCake + body + "\n  }\n}\n"))

  def testAbstractNameTypeAsName(): Unit =
    assertCleanLub("    def f(s: Symbol): Name = s.name")

  // --- controls ---

  def testControlPlainMember(): Unit =
    assertClean("    def f(s: Symbol): Type = s.tpeHK")

  def testControlNewAbstractType(): Unit =
    assertClean("    def f(s: Symbol): Symbol = s.newAbstractType(\"T\")")

  // scala/scala reify `Utils`: a member of a refinement typed path, `reifier.global`, is the singleton its
  // refinement declares, as seen from where `reifier` was selected (`NodePrinters.this.global`). Seen from
  // `reifier` instead, `Utils.this` was rewritten onto `reifier`, `reifier.global` became its own singleton,
  // and highlighting overflowed the stack.
  private val ReifierCake =
    """|class Global { class Position }
      |trait Errors { self: Reifier =>
      |  def defaultErrorPosition: global.Position = ???
      |}
      |trait NodePrinters { self: Utils =>
      |  def describe: String = "// produced from " + reifier.defaultErrorPosition
      |}
      |trait Utils extends NodePrinters {
      |  val global: Global
      |  lazy val reifier: Reifier { val global: Utils.this.global.type } = getReifier
      |  def getReifier: Reifier { val global: Utils.this.global.type } = ???
      |}
      |abstract class Reifier extends Errors with Utils {
      |  val global: Global
      |  override def getReifier: Reifier { val global: Reifier.this.global.type } =
      |    this.asInstanceOf[Reifier { val global: Reifier.this.global.type }]
      |}
      |""".stripMargin

  def testRefinementMemberOfSelfTypedSibling(): Unit =
    assertNothing(errorsFromScalaCode(ReifierCake.replace(
      "  def describe:", "  def position: global.Position = reifier.defaultErrorPosition\n  def describe:")))

  def testRefinementMemberIsNotAnotherUniversesMember(): Unit = assertMatches(errorsFromScalaCode(ReifierCake.replace(
    "  def describe:", "  def other(g: Global): g.Position = reifier.defaultErrorPosition\n  def describe:"))) {
    case Message.Error("reifier.defaultErrorPosition", _) :: Nil =>
  }

  // scala/scala `reflect/internal/Importers.scala`: inside `class StandardImporter { val from: SymbolTable }`,
  // members found on the path `from` are seen from `from`, each from its own owner. Seen from `from` at the
  // owner of `from` itself, this universe's `Importers.this` was rewritten onto `from`, so
  // `importModifiers(mods): Modifiers` became a `from.Modifiers`.
  private val ImportersCake =
    """|trait Universe {
      |  class Tree
      |  class Modifiers
      |  case class ClassDef(mods: Modifiers) extends Tree
      |}
      |trait Importers { to: SymbolTable =>
      |  abstract class StandardImporter {
      |    val from: SymbolTable
      |    def importModifiers(mods: from.Modifiers): Modifiers = new Modifiers
      |    def recreateTree(their: from.Tree): to.Tree = their match {
      |      case from.ClassDef(mods) => new ClassDef(importModifiers(mods))
      |    }
      |  }
      |}
      |abstract class SymbolTable extends Universe with Importers
      |""".stripMargin

  def testTypesOfThisUniverseInsideAnotherUniversesPattern(): Unit =
    assertNothing(errorsFromScalaCode(ImportersCake))

  def testBinderOfAnotherUniversesPatternIsThatUniverses(): Unit = assertMatches(errorsFromScalaCode(ImportersCake.replace(
    "new ClassDef(importModifiers(mods))", "{ val m: Modifiers = mods; new ClassDef(m) }"))) {
    case Message.Error("mods", _) :: Nil =>
  }
}
