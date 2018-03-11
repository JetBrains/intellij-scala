package org.jetbrains.plugins.scala.annotator

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}
import org.jetbrains.plugins.scala.util.TestUtils

class LiteralTypesHighlightingTest extends ScalaHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_13

  override def errorsFromScalaCode(scalaFileText: String): List[Message] = {
    import org.jetbrains.plugins.scala.project._
    myFixture.getModule.scalaCompilerSettings.additionalCompilerOptions = Seq("-Yliteral-types")
    super.errorsFromScalaCode(scalaFileText)
  }

  def folderPath = TestUtils.getTestDataPath + "/annotator/literalTypes/"

  def doTest(errorsFun: PartialFunction[List[Message], Unit] = PartialFunction.empty, fileText: Option[String] = None) {
    val text = fileText.getOrElse {
      val filePath = folderPath + getTestName(false) + ".scala"
      val ioFile: File = new File(filePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }
    val errors = errorsFromScalaCode(text)
    if (errorsFun == PartialFunction.empty) assertNothing(errors) else assertMatches(errors)(errorsFun)
  }

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

//TODO
//  def testSip23TailRec(): Unit = doTest()

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
      Error("4", "Expression of type Int(4) doesn't conform to expected type T_") ::
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

  def testSip23Bounds(): Unit = doTest()

  def testSip23Final(): Unit = doTest()

  def testSip23Folding(): Unit = doTest()

  def testSip23NamedDefault(): Unit = doTest()

  def testSip23NarrowNoEmptyRefinements(): Unit = doTest()

  def testSip23Narrow(): Unit = doTest()

  def testSip23NegativeLiterals(): Unit = doTest()

  def testSip23NoWiden(): Unit = doTest()

  def testSip23NumericLub(): Unit = doTest()

  def testSip23SingletonConvs(): Unit = doTest()

  def testSip23SingletonLub(): Unit = doTest()

  def testSip23Strings(): Unit = doTest()

  def testSip23SymbolsPos(): Unit = doTest()

  def testSip23ValueOfAlias(): Unit = doTest()

  def testSip23ValueOfCovariance(): Unit = doTest()

  def testSip23ValueOfThis(): Unit = doTest()

  def testSip23WidenPos(): Unit = doTest()

  def testSip23t6263(): Unit = doTest()

  def testSip23t6574(): Unit = doTest()

  def testSip23t6891(): Unit = doTest()

  def testSip23t900(): Unit = doTest()

  def testSip23UncheckedA(): Unit = doTest()

  def testLiteralTypeVarargs(): Unit = doTest()

  def testSip23Cast1(): Unit = doTest()

  def testSip23ImplicitResolution(): Unit = doTest()

  def testSip23Initialization0(): Unit = doTest()

  def testSip23Initialization1(): Unit = doTest()
//TODO highlights properly, but lacks dependencies, add later
//  def testSip23Macros1(): Unit = doTest()

//TODO 'Macros' does not highlight properly at all, fix this later
//  def testSip23Test2(): Unit = doTest()

  def testSip23Rangepos(): Unit = doTest()

  def testSip23RecConstant(): Unit = doTest()

  def testSip23TypeEquality(): Unit = doTest()

  def testSip23ValueOf(): Unit = doTest()

  def testSip23Widen2Pos(): Unit = doTest()

//  def testT(): Unit = {
//    val scalaText =
//      """
//        |trait O {
//        |  val f2: Int = 42
//        |}
//        |
//        |trait O2 extends O {
//        |  override val f2: String = "test"
//        |}
//      """.stripMargin
//    assertNothing(errorsFromScalaCode(scalaText))
//  }
}
