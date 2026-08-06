package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.PsiComment
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class DivergenceInfoTest extends ScalaLightCodeInsightFixtureTestCase {
  private def doTest(code: String): Unit = {
    val file = configureFromFileText(code).asInstanceOf[ScalaFile]

    val decls = file.depthFirst().filterByType[ScValueOrVariable]

    case class Format(declName: String, coreTp: String, complexity: String, topLevelTypeConstructors: String, coveringSet: String) {
      override def toString: String =
        s"""
           |$declName:
           |  - coreType:    $coreTp
           |  - complexity:  $complexity
           |  - topLevel:    $topLevelTypeConstructors
           |  - coveringSet: $coveringSet
           |""".stripMargin.trim
    }
    object Format {
      def fromError(decl: ScValueOrVariable, error: String): Format =
        Format(decl.declaredNames.head, error, error, error, error)

      def fromType(decl: ScValueOrVariable, tp: ScType): Format = {
        implicit val tpc: TypePresentationContext = TypePresentationContext(decl)
        implicit val context: Context = Context(decl)

        val info = DivergenceInfo(tp)
        Format(
          decl.declaredNames.head,
          info.coreType.presentableText,
          info.complexity.toString,
          info.topLevelTypeConstructors.map(_.presentableText).toSeq.sorted.mkString(", "),
          info.coveringSet.map(_.name).toSeq.sorted.mkString(", "),
        )
      }

      def fromComment(decl: ScValueOrVariable, comment: String): Format = {
        val components = comment.split('|')
        assert(components.length <= 4)
        val coreTp #:: complexity #:: topLevelTypeConstructors #:: coveringSet #:: _ = components.to(LazyList) ++ LazyList.continually("<missing>")
        Format(decl.declaredNames.head, coreTp.trim, complexity.trim, topLevelTypeConstructors.trim, coveringSet.trim)
      }
    }

    val (actual, expected) = decls.toSeq.map { decl =>
      val actual = decl.`type`().fold(
        err => Format.fromError(decl, err.toString),
        tp => Format.fromType(decl, tp)
      )

      val comment = decl.nextElementNotWhitespace.get.asInstanceOf[PsiComment].getText.stripPrefix("//").trim
      val expected = Format.fromComment(decl, comment)

      (actual, expected)
    }.unzip

    actual.mkString("\n") shouldBe expected.mkString("\n")
  }

  /*
   * <declaration/definition> // <core type> | <complexity> | <topLevelTypeConstructors> | <coveringSet>
   */
  def testPrimitives(): Unit = doTest(
    """
      |val char: Char     // Char | 1 | Char | Char
      |val byte: Byte     // Byte | 1 | Byte | Byte
      |val short: Short   // Short | 1 | Short | Short
      |val int: Int       // Int | 1 | Int | Int
      |val long: Long     // Long | 1 | Long | Long
      |val float: Float   // Float | 1 | Float | Float
      |val double: Double // Double | 1 | Double | Double
      |""".stripMargin
  )

  def testCommonLibTypes(): Unit = doTest(
    """
      |val list: List[Int]               // List[Int] | 2 | List | Int, List
      |val either: Either[String, Int]   // Either[String, Int] | 3 | Either | Either, Int, String
      |val seqSeq: Seq[Seq[Int]]         // Seq[Seq[Int]] | 3 | Seq | Int, Seq
      |val builder = Seq.newBuilder[Int] // mutable.Builder[Int, Seq[Int]] | 4 | mutable.Builder | Builder, Int, Seq
      |""".stripMargin
  )

  def testDeepTypes(): Unit = doTest(
    """
      |trait +[A, B]
      |trait A
      |trait B
      |trait C
      |trait AsString[A]
      |trait Assign[A, B]
      |
      |val a: A + B           // A + B | 3 | + | +, A, B
      |val b: B + A           // B + A | 3 | + | +, A, B
      |val c: AsString[A + B] // AsString[A + B] | 4 | AsString | +, A, AsString, B
      |val d: A + B + C       // A + B + C | 5 | + | +, A, B, C
      |""".stripMargin
  )

  def testTuple(): Unit = doTest(
    """
      |val a: (Int, Int)    // (Int, Int) | 3 | Tuple2 | Int, Tuple2
      |val b: (Int, String) // (Int, String) | 3 | Tuple2 | Int, String, Tuple2
      |""".stripMargin
  )

  def testCompoundTypes(): Unit = doTest(
    """
      |trait A
      |trait B
      |
      |val a: A with B                         // A with B | 2 | A, B | A, B
      |val b: A with { def test(): Unit = () } // A | 1 | A | A
      |""".stripMargin
  )

  def testFunctionType(): Unit = doTest(
    """
      |trait A
      |trait B
      |trait C
      |
      |val f1: A => B           // A => B | 3 | Function1 | A, B, Function1
      |val f2: A => B => C      // A => B => C | 5 | Function1 | A, B, C, Function1
      |val f3: (A, B) => C      // (A, B) => C | 4 | Function2 | A, B, C, Function2
      |val f4: (A, B) => C => A // (A, B) => C => A | 6 | Function2 | A, B, C, Function1, Function2
      |""".stripMargin
  )

  def testTypeDef(): Unit = doTest(
    """
      |type A = (Int, String)
      |
      |val a: A        // (Int, String) | 3 | Tuple2 | Int, String, Tuple2
      |val f: A => Int // ((Int, String)) => Int | 5 | Function1 | Function1, Int, String, Tuple2
      |""".stripMargin
  )
}
