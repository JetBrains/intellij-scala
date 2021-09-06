package org.jetbrains.plugins.scala
package annotator

class LiteralTypesHighlightingTest extends LiteralTypesHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13

  def testSip23Null(): Unit = doTest (
      Error("null", "Expression of type Null doesn't conform to expected type x.type") ::
      Error("null", "Expression of type Null doesn't conform to expected type y.type") :: Nil
  )

  def testSimple_1(): Unit = doTest()

  def testSip23Override(): Unit = doTest()

  def testSip23Override_1(): Unit = doTest (
      Error("f2", "Overriding type 5 does not conform to base type 4") ::
        Error("f5", "Overriding type 5 does not conform to base type 4") :: Nil
  )

  //TODO this should compile fine, will be fixed in compiler soon
  def testSip23TailRec(): Unit = doTest()

  def testSip23Uninit(): Unit = doTest(
       Error("_", "Unbound placeholder parameter") :: Nil
    )

  def testSip23Uninit_2(): Unit = doTest(
       Error("1", "Default initialization prohibited for literal-typed vars") :: Nil
    )

  def testSip23Widen(): Unit = doTest(
    Error("4", "Cannot upcast Int to 4") ::
      Error("(() => 4)", "Cannot upcast () => Int to () => 4") ::
      Error("(() => 4)", "Cannot upcast () => Int to () => 4") ::
      Error("4", "Cannot upcast Int to 4") ::
      Error("4", "Cannot upcast Int to 4") ::
      Error("5", "Type mismatch, expected: 4, actual: 5") ::
      Error("(() => (4, () => 5))", "Cannot upcast () => (Int, () => Int) to () => (4, () => 5)") ::
      Error("4", "Cannot upcast Int to 4") ::
      Error("4", "Cannot upcast Int to 4") ::
      Error("5", "Type mismatch, expected: 4, actual: 5") ::
      Error("5", "Expression of type 5 doesn't conform to expected type 4") ::
      Error("1 @unchecked", "Cannot upcast Int to 1") ::
      Nil
  )

  def testParameterized(): Unit = doTest()

  def testParameterized_1(): Unit = doTest()

  def testSip23t8323(): Unit = doTest(
     Error("f", "f(_root_.java.lang.String)_root_.java.lang.String is already defined in the scope") ::
      Error("f", "f(_root_.java.lang.String)_root_.java.lang.String is already defined in the scope") ::
      Nil
  )

  def testSip23AnyVsAnyref(): Unit = doTest()

  def testSip23Bounds(): Unit = doTest()

  def testSip23NotPossibleClause(): Unit = doTest()

  def testSip23Aliasing(): Unit = doTest()

  def testSip23Any(): Unit = doTest()

  def testSip23Final(): Unit = doTest()

  def testSip23NegativeLiterals(): Unit = doTest()

  def testSip23NumericLub(): Unit = doTest()

  def testSip23SingletonLub(): Unit = doTest()

  def testSip23Strings(): Unit = doTest()

  def testSip23ValueOfAlias(): Unit = doTest()

  def testSip23ValueOfCovariance(): Unit = doTest()

  def testSip23ValueOfThis(): Unit = doTest()

  def testSip23t6263(): Unit = doTest()

  def testSip23t6574(): Unit = doTest()

  def testSip23t6891(): Unit = doTest()

  def testSip23t900(): Unit = doTest()

  def testSip23UncheckedA(): Unit = doTest()

  def testSip23Cast1(): Unit = doTest()

  def testSip23ImplicitResolution(): Unit = doTest()

  def testSip23TypeEquality(): Unit = doTest()

  def testSip23ValueOf(): Unit = doTest()

  def testSip23Widen2Pos(): Unit = doTest()

  def testSip23Folding(): Unit = doTest()

  def testSip23NoWiden(): Unit = doTest()

  def testSip23SingletonConv(): Unit = doTest()

  def testSip23WidenPos(): Unit = doTest()

  def testSip23LiteralTypeVarargs(): Unit = doTest()

  def testSip23RecConstant(): Unit = doTest()

  def testConstantFolding(): Unit = doTest()

  def testSip23Rangepos(): Unit = doTest()

  def testConstantFoldingNeg(): Unit = doTest(
    Error("4", "Cannot upcast 3 to 4") ::
      Error("\"baz\"", "Cannot upcast \"foobar\" to \"baz\"") ::
      Error("false", "Cannot upcast true to false") ::
      Error("1", "Cannot upcast -1 to 1") ::
      Nil
  )

  def testSip23Initialization0(): Unit = doTest()

  def testSip23Initialization1(): Unit = doTest()

  def testSip23NarrowNoEmptyRefinements(): Unit = doTest()

  def testSip23Narrow(): Unit = doTest()

  def testAnnotLiteralType(): Unit = doTest(
     Error("23", "Class type required but (LiteralType: 23) found") :: Nil
  )

  def testWithSpaces(): Unit = doTest()

  def testSome(): Unit = doTest()

  def testSip23NamedDefault(): Unit = doTest()

  def testLiteralTypesLubs(): Unit = doTest()

  def testNoWidenExplicitLiteralType(): Unit = doTest(fileText = Some(
    """trait Foo[T]
      |object Bug {
      |  def foo() : Foo[32] = ???
      |  def bar(f : Foo[32]) : Unit = {}
      |  val f = foo()
      |  bar(f) //type mismatch, expected: Foo[32], actual: Foo[Int]
      |}
    """.stripMargin))
}
