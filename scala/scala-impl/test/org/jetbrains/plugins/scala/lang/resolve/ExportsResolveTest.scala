package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ExportsResolveTest extends SimpleResolveTestBase {
  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testExportSimple(): Unit =
    doResolveTest(
      s"""
         |trait B { def fo${REFTGT}o: Int = 123 }
         |
         |trait A {
         |  val b: B = ???
         |  export b._
         |  println(fo${REFSRC}o)
         |}
         |""".stripMargin
    )

  def testExportType(): Unit =
    doResolveTest(
      s"""
         |trait B { type Alia${REFTGT}s = Int }
         |trait A {
         |  val b: B = ???
         |  export b._
         |  val i: Ali${REFSRC}as = 123
         |}
         |""".stripMargin
    )

  def testExportAliased(): Unit =
    doResolveTest(
      s"""
         |trait B { def f${REFTGT}oo: Int = 123 }
         |trait A {
         |  val b: B = ???
         |  export b.{foo => bar}
         |  println(ba${REFSRC}r)
         |}
         |""".stripMargin
    )

  def testExportExported(): Unit =
    doResolveTest(
      s"""
         |trait A { def ${REFTGT}x: Int = 123 }
         |trait B { val a: A = ??? }
         |trait C { val b: B = ??? }
         |trait D extends C {
         |  export b.*
         |  export a.*
         |  println(${REFSRC}x)
         |}
         |""".stripMargin
    )

  def testExportExportedInParent(): Unit =
    doResolveTest(
      s"""
         |trait A { def f${REFTGT}oo: Int = 123 }
         |trait B { val a: A = ??? }
         |trait C { val b: B = ??? }
         |trait D extends C { export b._ }
         |trait E extends D {
         |  export a._
         |  println(f${REFSRC}oo)
         |}
         |
         |""".stripMargin
    )

  def testLangRefExample(): Unit =
    checkTextHasNoErrors(
      """
        |class BitMap
        |class InkJet
        |
        |class Printer:
        |  type PrinterType
        |  def print(bits: BitMap): Unit = ???
        |  def status: List[String] = ???
        |
        |class Scanner:
        |  def scan(): BitMap = ???
        |  def status: List[String] = ???
        |
        |class Copier:
        |  private val printUnit = new Printer { type PrinterType = InkJet }
        |  private val scanUnit = new Scanner
        |
        |  export scanUnit.scan
        |  export printUnit.{status => _, *}
        |
        |  printUnit.status ++ scanUnit.status
        |  scan()
        |
        |""".stripMargin
    )

  def testExportHiding(): Unit =
    checkHasErrorAroundCaret(
      s"""
         |trait A { def foo: Int = 123; def bar: Int = 456 }
         |trait B {
         |  val a: A = ???
         |  export a.{foo => _, bar}
         |  println(bar)
         |  println(f${CARET}oo)
         |}
         |
         |""".stripMargin
    )

  def testOutsideAccess(): Unit =
    doResolveTest(
      s"""
         |trait A { def f${REFTGT}oo: Int = 123 }
         |trait B {
         |  val a: A = ???
         |  export a.foo
         |}
         |
         |object O {
         |  val b: B = ???
         |  b.fo${REFSRC}o
         |}
         |""".stripMargin
    )

  def testExportWhatever(): Unit =
    checkTextHasNoErrors(
      """
        |object E {
        |  def qux: String = ???
        |}
        |trait A {
        |  def bar: Double = 2d
        |}
        |
        |class X {
        |  val a: A = ???
        |  export a._
        |  export E._
        |  println(bar)
        |  println(qux)
        |}
        |
        |""".stripMargin
    )

  def testSubst(): Unit =
    checkTextHasNoErrors(
      """
        |trait A { def foo: this.type = ??? }
        |trait B {
        |  val a: A = ???
        |  export a._
        |  val atype: a.type = foo
        |}
        |""".stripMargin
    )

  def testWildcardSelector(): Unit =
    checkTextHasNoErrors(
      """
        |trait A { def x: Int = 123; def y: Int = 123 }
        |trait B {
        |  val a: A = ???
        |  export a.{ * }
        |  x
        |  y
        |}
        |""".stripMargin
    )

  def testSCL20702(): Unit =
    checkTextHasNoErrors(
      """
        |class IMap[K, V] {
        |  val m = Map.empty[K, V]
        |  export m.*
        |}
        |
        |object m extends IMap[String, String]()
        |
        |val xs: Iterable[String] = m.values // xs type hints is Iterable[V]
        |
        |""".stripMargin
    )

  def testSCL20917(): Unit =
    checkTextHasNoErrors(
      """
        |class Context:
        |  def contextMethod: String = ???
        |
        |class Script1(val ctxParam1: Context):
        |  export ctxParam1.* //ERROR: not resolved
        |
        |class Script2(ctxParam2: Context):
        |  export ctxParam2.*
        |
        |class Script3(ctxParam3: Context):
        |  export ctxParam3.* //ERROR: not resolved
        |
        |  def scriptMethod: String = ???
        |
        |class Script4:
        |  val ctxField: Context = ???
        |  export ctxField.*
        |
        |  def scriptMethod: String = ???
        |
        |object Usage {
        |  (null: Script1).contextMethod //ERROR: not resolved
        |  (null: Script2).contextMethod //OK
        |  (null: Script3).contextMethod //ERROR: not resolved
        |  (null: Script4).contextMethod //OK
        |}
        |""".stripMargin
    )
}
