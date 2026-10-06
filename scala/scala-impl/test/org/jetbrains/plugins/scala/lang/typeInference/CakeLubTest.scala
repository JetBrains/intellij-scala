package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.assertNothing

// A lub of cake classes seen from a path must keep the path: scalac computes it
// over the base type sequences of `global.AliasTypeSymbol` etc., i.e. as seen
// from `global`. Distilled from scala/scala's Typers.scala (SCL-21947).
class CakeLubTest extends TypeInferenceTestBase {

  private val Cake =
    """
      |trait Names { self: SymbolTable =>
      |  abstract class Name
      |  abstract class TermName extends Name
      |  abstract class TypeName extends Name
      |}
      |trait StdNames { self: SymbolTable =>
      |  abstract class CommonNames { type NameType >: Null <: Name }
      |  abstract class TermNames extends CommonNames { override type NameType = TermName }
      |  object nme extends TermNames { val ERROR: NameType = null }
      |}
      |trait Symbols { self: SymbolTable =>
      |  abstract class Symbol {
      |    type NameType >: Null <: Name
      |    def name: NameType = null
      |    def setInfo(i: Int): this.type = this
      |    def newAliasType(n: String): AliasTypeSymbol = null
      |    def newAbstractType(n: String): AbstractTypeSymbol = null
      |  }
      |  abstract class TypeSymbol extends Symbol
      |  abstract class AliasTypeSymbol extends TypeSymbol
      |  abstract class AbstractTypeSymbol extends TypeSymbol
      |}
      |abstract class SymbolTable extends Names with StdNames with Symbols
      |class Global extends SymbolTable
      |trait Analyzer extends Typers { val global: Global }
      |trait Typers { self: Analyzer =>
      |  import global._
      |""".stripMargin

  private def doCakeTest(body: String, expected: String): Unit =
    doTest(s"$Cake$body\n}\n//$expected")

  def testSiblingSubclasses(): Unit = doCakeTest(
    s"  def f(a: AliasTypeSymbol, b: AbstractTypeSymbol, c: Boolean): Symbol = ${START}if (c) a else b$END",
    "Typers.this.global.TypeSymbol"
  )

  def testSiblingSubclassesViaThisType(): Unit = doCakeTest(
    s"""  def f(o: Symbol, c: Boolean): Symbol = ${START}if (c) o.newAliasType("a") setInfo 1 else o.newAbstractType("b") setInfo 2$END""",
    "Typers.this.global.TypeSymbol"
  )

  def testSymbolAndSiblingSubclasses(): Unit = doCakeTest(
    s"""  def f(t: Symbol, o: Symbol, c: Boolean, d: Boolean): Symbol = ${START}if (c) t else { if (d) o.newAliasType("a") else o.newAbstractType("b") }$END""",
    "Typers.this.global.Symbol"
  )

  def testNameTypes(): Unit = doCakeTest(
    s"  def f(s: Symbol, c: Boolean): Name = ${START}if (c) nme.ERROR else s.name$END",
    "Typers.this.global.Name"
  )

  // Sibling case classes, three-way lubs and a lub through a base type argument
  // (Typers.scala 1986, 2604, 5596-5637, 6254).
  private val TreesCake =
    """
      |trait Symbols { self: SymbolTable => abstract class Symbol }
      |trait Scopes { self: SymbolTable =>
      |  class Scope extends Iterable[Symbol] { def iterator: Iterator[Symbol] = Iterator.empty }
      |}
      |trait Trees { self: SymbolTable =>
      |  abstract class Tree
      |  trait RefTree extends Tree
      |  abstract class TypTree extends Tree
      |  case class Select(q: Tree, n: String) extends RefTree
      |  case class SelectFromTypeTree(q: Tree, n: String) extends TypTree with RefTree
      |  case class TypeTree() extends TypTree
      |  case class Annotated(annot: Tree, arg: Tree) extends Tree
      |}
      |abstract class SymbolTable extends Symbols with Scopes with Trees
      |trait NscTrees extends Trees { self: Global =>
      |  case class DocDef(c: String, d: Tree) extends Tree
      |  case class TypeTreeWithDeferredRefCheck(t: TypeTree) extends TypTree
      |}
      |class Global extends SymbolTable with NscTrees
      |trait Analyzer extends Typers { val global: Global }
      |trait Typers { self: Analyzer =>
      |  import global._
      |""".stripMargin

  private def assertCleanTrees(body: String): Unit =
    assertNothing(errorsFromScalaCode(s"$TreesCake$body\n}\n"))

  def testSiblingCaseClassesSameLayer(): Unit =
    assertCleanTrees("  def f(t: Tree, c: Boolean): Tree = { val r = if (c) Select(t, \"a\") else SelectFromTypeTree(t, \"b\"); r }")

  def testSiblingCaseClassesToBaseClass(): Unit =
    assertCleanTrees("  def f(a: Annotated, c: Boolean): Tree = { val r = if (c) TypeTree() else a; r }")

  def testSiblingCaseClassesAcrossLayers(): Unit =
    assertCleanTrees("  def f(t: TypeTree, c: Boolean): Tree = { val r = if (c) TypeTreeWithDeferredRefCheck(t) else t; r }")

  // Folded pairwise: lub(DocDef, Annotated) must keep the prefix for `t` to subsume it.
  def testThreeTreesWithBaseClass(): Unit =
    assertCleanTrees("  def f(t: Tree, d: DocDef, a: Annotated, c: Int): Tree = { val r = c match { case 0 => d; case 1 => a; case _ => t }; r }")

  def testThroughBaseTypeArgument(): Unit =
    assertCleanTrees("  def f(s: Scope, l: List[Symbol], c: Boolean): Symbol = { val m = if (c) l else s; m.head }")

  def testControlSubsumingSide(): Unit =
    assertCleanTrees("  def f(t: Tree, d: DocDef, c: Boolean): Tree = { val r = if (c) d else t; r }")
}
