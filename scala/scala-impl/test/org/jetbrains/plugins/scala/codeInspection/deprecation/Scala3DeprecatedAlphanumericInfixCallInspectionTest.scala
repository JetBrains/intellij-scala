package org.jetbrains.plugins.scala.codeInspection.deprecation

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.quickfix.{ConvertFromInfixExpressionQuickFix, ConvertFromInfixPatternQuickFix, ConvertFromInfixTypeQuickFix, WrapInBackticksQuickFix}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class Scala3DeprecatedAlphanumericInfixCallInspectionTestBase extends ScalaInspectionTestBase {

  import Scala3DeprecatedAlphanumericInfixCallInspectionTestBase.testText

  protected def additionalCompilerOptions: Seq[String]

  override protected val classOfInspection = classOf[Scala3DeprecatedAlphanumericInfixCallInspection]
  override protected val description = " is not declared `infix`; it should not be used as infix operator"

  override protected def descriptionMatches(s: String) = s != null && s.endsWith(description)

  override protected def setUp(): Unit = {
    super.setUp()
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
    profile.setSettings(newSettings)
  }

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def createTestText(text: String) = testText(text)

  protected val DeprecationFlag = "-deprecation"
  protected val SourceFutureFlag = "-source:future"
}

object Scala3DeprecatedAlphanumericInfixCallInspectionTestBase {
  private def testText(fileText: String): String =
    s"""class C:
       |  infix def op(x: Int): Int = ???
       |  def `bop`(x: Int) = ???
       |  def meth(x: Int): Int = ???
       |  def matching(x: Int => Int) = ???
       |  def +(x: Int): Int = ???
       |
       |object C:
       |  given AnyRef with
       |    extension (x: C)
       |      infix def iop (y: Int) = ???
       |      def mop (y: Int) = ???
       |      def ++ (y: Int) = ???
       |
       |infix class Or[X, Y]
       |class AndC[X, Y]
       |infix type And[X, Y] = AndC[X, Y]
       |type &&[X, Y] = AndC[X, Y]
       |
       |type SwappedMap[X, Y] = scala.collection.Map[Y, X]
       |
       |case class Pair[T](x: T, y: T)
       |infix case class Q[T](x: T, y: T)
       |
       |object PP {
       |  infix def unapply[T](x: Pair[T]): Option[(T, T)] = Some((x.x, x.y))
       |}
       |
       |object PP2 {
       |  def unapply[T](x: Pair[T]): Option[(T, T)] = Some((x.x, x.y))
       |}
       |
       |val c = C()
       |val p = Pair(1, 2)
       |val q = Q(1, 2)
       |
       |$fileText
       |""".stripMargin
}

abstract class DisabledScala3DeprecatedAlphanumericInfixCallInspectionTestBase
  extends Scala3DeprecatedAlphanumericInfixCallInspectionTestBase {

  // Should be highlighted when both `-source:future` and `-deprecation` flags enabled
  def testMethodInfixCall(): Unit = checkTextHasNoErrors(s"c ${START}meth$END 2")
}

class Scala3DeprecatedAlphanumericInfixCallInspectionTest_NoCompilerOptions
  extends DisabledScala3DeprecatedAlphanumericInfixCallInspectionTestBase {
  override protected val additionalCompilerOptions = Seq.empty
}

class Scala3DeprecatedAlphanumericInfixCallInspectionTest_OnlyDeprecationEnabled
  extends DisabledScala3DeprecatedAlphanumericInfixCallInspectionTestBase {
  override protected val additionalCompilerOptions = Seq(DeprecationFlag)
}

class Scala3DeprecatedAlphanumericInfixCallInspectionTest_OnlySourceFutureEnabled
  extends DisabledScala3DeprecatedAlphanumericInfixCallInspectionTestBase {
  override protected val additionalCompilerOptions = Seq(SourceFutureFlag)
}

class Scala3DeprecatedAlphanumericInfixCallInspectionTest
  extends Scala3DeprecatedAlphanumericInfixCallInspectionTestBase {

  import Scala3DeprecatedAlphanumericInfixCallInspectionTest.allCasesWithHighlighting

  override protected val additionalCompilerOptions: Seq[String] =
    Seq(DeprecationFlag, SourceFutureFlag)

  private val wrapInBackticksHint = WrapInBackticksQuickFix.message
  private val convertFromInfixExprHint = ConvertFromInfixExpressionQuickFix.message
  private val convertFromInfixPatternHint = ConvertFromInfixPatternQuickFix.message
  private val convertFromInfixTypeHint = ConvertFromInfixTypeQuickFix.message

  def testMethodInfixCall(): Unit = {
    val code = s"c ${START}meth$END 2"
    checkTextHasError(code)

    testQuickFix(code, "c `meth` 2", wrapInBackticksHint)
    testQuickFix(code, "c.meth(2)", convertFromInfixExprHint)
  }

  def testExtensionMethodInfixCall(): Unit = {
    val code = s"c ${START}mop$END 2"
    checkTextHasError(code)

    testQuickFix(code, "c `mop` 2", wrapInBackticksHint)
    testQuickFix(code, "c.mop(2)", convertFromInfixExprHint)
  }

  def testInfixMethodInfixCall(): Unit = checkTextHasNoErrors("c op 2")

  def testInfixExtensionMethodInfixCall(): Unit = checkTextHasNoErrors("c iop 2")

  def testSymbolicMethodInfixCall(): Unit = checkTextHasNoErrors("c + 2")

  def testSymbolicExtensionMethodInfixCall(): Unit = checkTextHasNoErrors("c ++ 2")

  def testMethodCall(): Unit = checkTextHasNoErrors("c.meth(2)")

  def testExtensionMethodCall(): Unit = checkTextHasNoErrors("c.mop(2)")

  def testInfixMethodCall(): Unit = checkTextHasNoErrors("c.op(2)")

  def testInfixExtensionMethodCall(): Unit = checkTextHasNoErrors("c.iop(2)")

  def testSymbolicMethodCall(): Unit = checkTextHasNoErrors("c.+(2)")

  def testSymbolicExtensionMethodCall(): Unit = checkTextHasNoErrors("c.++(2)")

  def testBacktickedMethodInfixCall(): Unit = {
    val code = s"c ${START}bop$END 2"
    checkTextHasError(code)

    testQuickFix(code, "c `bop` 2", wrapInBackticksHint)
    testQuickFix(code, "c.bop(2)", convertFromInfixExprHint)
  }

  def testBacktickedMethodInfixBacktickedCall(): Unit = checkTextHasNoErrors("c `bop` 2")

  def testMethodDefinedInScala2InfixCall(): Unit = checkTextHasNoErrors("1 to 2")

  def testMethodCallWithBraces(): Unit = checkTextHasNoErrors {
    s"""
       |c meth {
       |  3
       |}
       |""".stripMargin
  }

  def testMethodCallWithMatch(): Unit = checkTextHasNoErrors {
    s"""
       |c matching {
       |  case x => x
       |}
       |""".stripMargin
  }

  def testInfixScala3ClassInInfixType(): Unit = checkTextHasNoErrors("val x1: Int Or String = ???")

  def testClassInInfixType(): Unit = {
    val code = s"val x2: Int ${START}AndC$END String = ???"
    checkTextHasError(code)

    testQuickFix(code, "val x2: Int `AndC` String = ???", wrapInBackticksHint)
    testQuickFix(code, "val x2: AndC[Int, String] = ???", convertFromInfixTypeHint)
  }

  def testClassInBacktickedInfixType(): Unit = checkTextHasNoErrors("val x3: Int `AndC` String = ???")

  def testInfixTypeInInfixType(): Unit = checkTextHasNoErrors("val x4: Int And String = ???")

  def testSymbolicTypeInInfixType(): Unit = checkTextHasNoErrors("val x5: Int && String = ???")

  def testTypeInInfixType(): Unit = {
    val code = s"val x6: Int ${START}SwappedMap$END String = ???"
    checkTextHasError(code)

    testQuickFix(code, "val x6: Int `SwappedMap` String = ???", wrapInBackticksHint)
    testQuickFix(code, "val x6: SwappedMap[Int, String] = ???", convertFromInfixTypeHint)
  }

  def testExtractorInPattern(): Unit = checkTextHasNoErrors("val Pair(_, _) = p")

  def testExtractorInInfixPattern(): Unit = {
    val code = s"val _ ${START}Pair$END _ = p"
    checkTextHasError(code)
    testQuickFix(code, "val _ `Pair` _ = p", wrapInBackticksHint)
    testQuickFix(code, "val Pair(_, _) = p", convertFromInfixPatternHint)
  }

  def testExtractorInBacktickedInfixPattern(): Unit = checkTextHasNoErrors("val _ `Pair` _ = p")

  def testInfixExtractorInInfixPattern(): Unit = checkTextHasNoErrors("val (_ PP _) = p: @unchecked")

  def testCustomExtractorInInfixPattern(): Unit = {
    val code = s"val (_ ${START}PP2$END _) = p: @unchecked"
    checkTextHasError(code)
    testQuickFix(code, "val (_ `PP2` _) = p: @unchecked", wrapInBackticksHint)
    testQuickFix(code, "val PP2(_, _) = p: @unchecked", convertFromInfixPatternHint)
  }

  def testExtractorOfInfixCaseClassInPattern(): Unit = checkTextHasNoErrors("val Q(_, _)")

  def testExtractorOfInfixCaseClassInInfixPattern(): Unit = checkTextHasNoErrors("val _ Q _ = q")

  def testMultipleHighlinghtings(): Unit = checkTextHasError(allCasesWithHighlighting)

  def testQuickFixAllWrappingInBackticks(): Unit = {
    val expectedAfterWrapping =
      """
        |c `meth` 2
        |c `mop` 2
        |c op 2
        |c iop 2
        |c + 2
        |c ++ 2
        |
        |c.meth(2)
        |c.mop(2)
        |c.op(2)
        |c.iop(2)
        |c.+(2)
        |c.++(2)
        |
        |c `bop` 2
        |c `bop` 2
        |1 to 2
        |
        |c meth {
        |  3
        |}
        |
        |c matching {
        |  case x => x
        |}
        |
        |val x1: Int Or String = ???
        |val x2: Int `AndC` String = ???
        |val x3: Int `AndC` String = ???
        |val x4: Int And String = ???
        |val x5: Int && String = ???
        |val x6: Int `SwappedMap` String = ???
        |
        |val Pair(_, _) = p
        |val _ `Pair` _ = p
        |val _ `Pair` _ = p
        |val (_ PP _) = p: @unchecked
        |val (_ `PP2` _) = p: @unchecked
        |
        |val Q(_, _)
        |val _ Q _ = q
        |""".stripMargin
    testQuickFixAllInFile(allCasesWithHighlighting, expectedAfterWrapping, wrapInBackticksHint)
  }

  def testQuickFixAllConvertingFromInfixExpr(): Unit = {
    val expectedAfterConversion =
      """
        |c.meth(2)
        |c.mop(2)
        |c op 2
        |c iop 2
        |c + 2
        |c ++ 2
        |
        |c.meth(2)
        |c.mop(2)
        |c.op(2)
        |c.iop(2)
        |c.+(2)
        |c.++(2)
        |
        |c.bop(2)
        |c `bop` 2
        |1 to 2
        |
        |c meth {
        |  3
        |}
        |
        |c matching {
        |  case x => x
        |}
        |
        |val x1: Int Or String = ???
        |val x2: Int AndC String = ???
        |val x3: Int `AndC` String = ???
        |val x4: Int And String = ???
        |val x5: Int && String = ???
        |val x6: Int SwappedMap String = ???
        |
        |val Pair(_, _) = p
        |val _ Pair _ = p
        |val _ `Pair` _ = p
        |val (_ PP _) = p: @unchecked
        |val (_ PP2 _) = p: @unchecked
        |
        |val Q(_, _)
        |val _ Q _ = q
        |""".stripMargin
    testQuickFixAllInFile(allCasesWithHighlighting, expectedAfterConversion, convertFromInfixExprHint)
  }

  def testQuickFixAllConvertingFromInfixType(): Unit = {
    val expectedAfterConversion =
      """
        |c meth 2
        |c mop 2
        |c op 2
        |c iop 2
        |c + 2
        |c ++ 2
        |
        |c.meth(2)
        |c.mop(2)
        |c.op(2)
        |c.iop(2)
        |c.+(2)
        |c.++(2)
        |
        |c bop 2
        |c `bop` 2
        |1 to 2
        |
        |c meth {
        |  3
        |}
        |
        |c matching {
        |  case x => x
        |}
        |
        |val x1: Int Or String = ???
        |val x2: AndC[Int, String] = ???
        |val x3: Int `AndC` String = ???
        |val x4: Int And String = ???
        |val x5: Int && String = ???
        |val x6: SwappedMap[Int, String] = ???
        |
        |val Pair(_, _) = p
        |val _ Pair _ = p
        |val _ `Pair` _ = p
        |val (_ PP _) = p: @unchecked
        |val (_ PP2 _) = p: @unchecked
        |
        |val Q(_, _)
        |val _ Q _ = q
        |""".stripMargin
    testQuickFixAllInFile(allCasesWithHighlighting, expectedAfterConversion, convertFromInfixTypeHint)
  }

  def testQuickFixAllConvertingFromInfixPattern(): Unit = {
    val expectedAfterConversion =
      """
        |c meth 2
        |c mop 2
        |c op 2
        |c iop 2
        |c + 2
        |c ++ 2
        |
        |c.meth(2)
        |c.mop(2)
        |c.op(2)
        |c.iop(2)
        |c.+(2)
        |c.++(2)
        |
        |c bop 2
        |c `bop` 2
        |1 to 2
        |
        |c meth {
        |  3
        |}
        |
        |c matching {
        |  case x => x
        |}
        |
        |val x1: Int Or String = ???
        |val x2: Int AndC String = ???
        |val x3: Int `AndC` String = ???
        |val x4: Int And String = ???
        |val x5: Int && String = ???
        |val x6: Int SwappedMap String = ???
        |
        |val Pair(_, _) = p
        |val Pair(_, _) = p
        |val _ `Pair` _ = p
        |val (_ PP _) = p: @unchecked
        |val PP2(_, _) = p: @unchecked
        |
        |val Q(_, _)
        |val _ Q _ = q
        |""".stripMargin
    testQuickFixAllInFile(allCasesWithHighlighting, expectedAfterConversion, convertFromInfixPatternHint)
  }
}

object Scala3DeprecatedAlphanumericInfixCallInspectionTest {

  import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  private val allCasesWithHighlighting =
    s"""
       |c ${START}meth$END 2
       |c ${START}mop$END 2
       |c op 2
       |c iop 2
       |c + 2
       |c ++ 2
       |
       |c.meth(2)
       |c.mop(2)
       |c.op(2)
       |c.iop(2)
       |c.+(2)
       |c.++(2)
       |
       |c ${START}bop$END 2
       |c `bop` 2
       |1 to 2
       |
       |c meth {
       |  3
       |}
       |
       |c matching {
       |  case x => x
       |}
       |
       |val x1: Int Or String = ???
       |val x2: Int ${START}AndC$END String = ???
       |val x3: Int `AndC` String = ???
       |val x4: Int And String = ???
       |val x5: Int && String = ???
       |val x6: Int ${START}SwappedMap$END String = ???
       |
       |val Pair(_, _) = p
       |val _ ${START}Pair$END _ = p
       |val _ `Pair` _ = p
       |val (_ PP _) = p: @unchecked
       |val (_ ${START}PP2${END} _) = p: @unchecked
       |
       |val Q(_, _)
       |val _ Q _ = q
       |""".stripMargin
}
