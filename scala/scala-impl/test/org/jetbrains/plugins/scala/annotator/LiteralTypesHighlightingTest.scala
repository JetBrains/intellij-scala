package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.util.TestUtils

class LiteralTypesHighlightingTest extends LiteralTypesHighlightingTestBase {

  def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/"

  def testSip23Null(): Unit = doTest{
    case Error(_, "Type mismatch, found: Null(null), required: x.type") ::
      Error(_, "Expression of type Null(null) doesn't conform to expected type x.type") ::
      Error(_, "Type mismatch, found: Null(null), required: y.type") ::
      Error(_, "Expression of type Null(null) doesn't conform to expected type y.type") :: Nil =>
  }

  def testSimple(): Unit = doTest()

  def testSimple_1(): Unit = doTest()

  def testSip23Override(): Unit = doTest()

  def testSip23Override_1(): Unit = doTest{
      case Error("f2", "Overriding type Int(5) does not conform to base type Int(4)") ::
        Error("f5", "Overriding type Int(5) does not conform to base type Int(4)") :: Nil =>
    }

  def testSip23Symbols(): Unit = doTest{
      case Error("sym0", "Type mismatch, found: Symbol, required: Symbol('s)") ::
        Error("sym0", "Expression of type Symbol doesn't conform to expected type Symbol('s)") ::
        Error("sym3", "Type mismatch, found: Symbol, required: Symbol('s)") ::
        Error("sym3", "Expression of type Symbol doesn't conform to expected type Symbol('s)") :: Nil =>
    }

  //TODO this should compile fine, will be fixed in compiler soon
  def testSip23TailRec(): Unit = doTest()

  def testSip23Uninit(): Unit = doTest{
      case Error(_, "Unbound placeholder parameter") :: Nil =>
    }

  def testSip23Uninit_2(): Unit = doTest{
      case Error(_, "Default initialization prohibited for literal-typed vars") :: Nil =>
    }

  def testSip23Widen(): Unit = doTest{
    case Error("f0", "Type mismatch, found: Int, required: Int(4)") ::
      Error("f0", "Expression of type Int doesn't conform to expected type Int(4)") ::
      Error("f2", "Type mismatch, found: () => Int, required: () => Int(4)") ::
      Error("f2","Expression of type () => Int doesn't conform to expected type () => Int(4)") ::
      Error("f3", "Type mismatch, found: () => Int, required: () => Int(4)") ::
      Error("f3", "Expression of type () => Int doesn't conform to expected type () => Int(4)") ::
      Error("f5", "Type mismatch, found: Int, required: Int(4)") ::
      Error("f5", "Expression of type Int doesn't conform to expected type Int(4)") ::
      Error("f6", "Type mismatch, found: Int, required: Int(4)") ::
      Error("f6", "Expression of type Int doesn't conform to expected type Int(4)") ::
      Error("5", "Type mismatch, expected: Int(4), actual: Int(5)") ::
      Error("f9", "Type mismatch, found: () => (Int, () => Int), required: () => (Int(4), () => Int(5))") ::
      Error("f9", "Expression of type () => (Int, () => Int) doesn't conform to expected type () => (Int(4), () => Int(5))") ::
      Error("f11", "Type mismatch, found: Int, required: Int(4)") ::
      Error("f11", "Expression of type Int doesn't conform to expected type Int(4)") ::
      Error("f12", "Type mismatch, found: Int, required: Int(4)") ::
      Error("f12", "Expression of type Int doesn't conform to expected type Int(4)") ::
      Error("5", "Type mismatch, expected: Int(4), actual: Int(5)") ::
      Error("5", "Expression of type Int(5) doesn't conform to expected type Int(4)") ::
      Error("annot0", "Type mismatch, found: Int, required: Int(1)") ::
      Error("annot0", "Expression of type Int doesn't conform to expected type Int(1)") ::
      Nil =>
  }

  def testParameterized(): Unit = doTest()

  def testParameterized_1(): Unit = doTest()

  def testSip23t8323(): Unit = doTest{
    case Error("f", "f(_root_.java.lang.String) is already defined in the scope") ::
      Error("f", "f(_root_.java.lang.String) is already defined in the scope") ::
      Nil =>
  }

  def testSip23AnyVsAnyref(): Unit = doTest()

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

  def testConstantFoldingNeg(): Unit = doTest {
    case Error("1 + 2", "Type mismatch, found: Int(3), required: Int(4)") ::
      Error("1 + 2", "Expression of type Int(3) doesn't conform to expected type Int(4)") ::
      Error("\"foo\" + \"bar\"", "Type mismatch, found: String(foobar), required: String(baz)") ::
      Error("\"foo\" + \"bar\"", "Expression of type String(foobar) doesn't conform to expected type String(baz)") ::
      Error("true || false", "Type mismatch, found: Boolean(true), required: Boolean(false)") ::
      Error("true || false", "Expression of type Boolean(true) doesn't conform to expected type Boolean(false)") ::
      Error("-1", "Type mismatch, found: Int(-1), required: Int(1)") ::
      Error("-1", "Expression of type Int(-1) doesn't conform to expected type Int(1)") ::
      Nil =>
  }

  def testDefaultIsOff(): Unit = doTest(fileText = Some(
    """
      |class O {
      |  val x: -1 = -1
      |  1: 1
      |}
    """.stripMargin), settingOn = false, errorsFun = {
      case Error("-1", "Wrong type '-1', for literal types support please use '-Yliteral-types' compiler flag") ::
        Error("1", "Wrong type '1', for literal types support please use '-Yliteral-types' compiler flag") ::
        Nil => })

  def testSip23Initialization0(): Unit = doTest()

  def testSip23Initialization1(): Unit = doTest()

  def testSip23NarrowNoEmptyRefinements(): Unit = doTest()

  def testSip23SymbolsPos(): Unit = doTest()

  //below are problematic tests
}
