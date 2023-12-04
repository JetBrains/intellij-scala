package org.jetbrains.plugins.scala.lang.psi

import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven
import org.jetbrains.plugins.scala.util.GeneratedTestSuiteFactory

class GenerateGivenNameTest extends TestCase

object GenerateGivenNameTest extends GeneratedTestSuiteFactory {
  override type TD = GivenNameTestData

  /**
   * All test cases.
   *
   * See CheckGenerateGivenNameTestDataTest, which uses the real scala compiler to check
   * if these testcases compile correctly.
   */
  override lazy val testData: Seq[GivenNameTestData] = Seq(
    //////////////////// atoms /////////////////
    GivenNameTestData(
      """
        |given Int = 0
        |""".stripMargin,
      "given_Int"
    ),
    GivenNameTestData(
      """
        |given String = ""
        |""".stripMargin,
      "given_String"
    ),
    GivenNameTestData(
      """
        |given java.lang.String = ""
        |""".stripMargin,
      "given_String"
    ),
    GivenNameTestData(
      """
        |object O
        |given O.type = ???
        |""".stripMargin,
      "given_O_type"
    ),
    GivenNameTestData(
      """
        |given 1 = ???
        |""".stripMargin,
      "given_"
    ),
    GivenNameTestData(
      """
        |given (1, 2) = ???
        |""".stripMargin,
      "given__"
    ),
    GivenNameTestData(
      """
        |given (1, Int) = ???
        |""".stripMargin,
      "given__Int"
    ),
    GivenNameTestData(
      """
        |given (Byte, (Short, Int)) = ???
        |""".stripMargin,
      "given_Byte_Short_Int"
    ),

    //////////////////// functions /////////////////
    GivenNameTestData(
      """
        |given (() => Set[Int]) = ???
        |""".stripMargin,
      "given_Set_Int"
    ),
    GivenNameTestData(
      """
        |given ((Float, Double) => Int) = ???
        |""".stripMargin,
      "given_Float_Double_to_Int"
    ),
    GivenNameTestData(
      """
        |given ((Float) => "blub") = ???
        |""".stripMargin,
      "given_Float_to_"
    ),
    GivenNameTestData(
      """
        |given ((Float, Double) => Int => String) = ???
        |""".stripMargin,
      "given_Float_Double_to_Int_to_String"
    ),
    GivenNameTestData(
      """
        |given ((Float, Double) => Int => String => Short) = ???
        |""".stripMargin,
      "given_Float_Double_to_Int_to_String_to_Short"
    ),
    GivenNameTestData(
      """
        |given ((Map[Int, Float]) => Set[Int]) = ???
        |""".stripMargin,
      "given_Map_to_Set_Int"
    ),
    GivenNameTestData(
      """
        |given (Short => Float ?=> Int) = ???
        |""".stripMargin,
      "given_Short_to_Float_to_Int"
    ),
    GivenNameTestData(
      """
        |given ([X] => X => Int) = ???
        |""".stripMargin,
      "given_X"
    ),
    GivenNameTestData(
      """
        |given ([A, B] =>> Set[Int])[Seq[Float], Iterator[Byte]] = ???
        |""".stripMargin,
      "given_Set_Int_Seq_Iterator"
    ),

    //////////////////// Single generics /////////////////
    GivenNameTestData(
      """
        |given Seq[Int] = Seq()
        |""".stripMargin,
      "given_Seq_Int"
    ),
    GivenNameTestData(
      """
        |given Seq[?] = Seq()
        |""".stripMargin,
      "given_Seq_"
    ),
    GivenNameTestData(
      """
        |given java.util.Set[Int] = null
        |""".stripMargin,
      "given_Set_Int"
    ),
    GivenNameTestData(
      """
        |given Seq[Seq[Int]] = null
        |""".stripMargin,
      "given_Seq_Seq"
    ),
    GivenNameTestData(
      """
        |given java.util.Set[java.util.Set[Int]] = null
        |""".stripMargin,
      "given_Set_Set"
    ),
    GivenNameTestData(
      """
        |given [T]: Seq[T] = ???
        |""".stripMargin,
      "given_Seq_T"
    ),
    GivenNameTestData(
      """
        |trait X {
        |  type Assoc
        |}
        |given [T <: X]: Seq[X#Assoc] = ???
        |""".stripMargin,
      "given_Seq_Assoc"
    ),
    GivenNameTestData(
      """
        |given [T]: T = ???
        |""".stripMargin,
      "given_T"
    ),
    GivenNameTestData(
      """
        |trait X {
        |  type Assoc
        |}
        |given [T <: X]: X#Assoc = ???
        |""".stripMargin,
      "given_Assoc"
    ),
    GivenNameTestData(
      """
        |trait X {
        |  type Assoc
        |}
        |given [T <: X]: (T, X#Assoc) = ???
        |""".stripMargin,
      "given_T_Assoc"
    ),
    GivenNameTestData(
      """
        |trait X {
        |  type Assoc
        |}
        |given [T <: X]: (T, X#Assoc) = ???
        |""".stripMargin,
      "given_T_Assoc"
    ),

    //////////////////// Two generics /////////////////
    GivenNameTestData(
      """
        |given Map[Int, Int] = null
        |""".stripMargin,
      "given_Map_Int_Int"
    ),
    GivenNameTestData(
      """
        |given Map[Set[String], Int] = null
        |""".stripMargin,
      "given_Map_Set_Int"
    ),
    GivenNameTestData(
      """
        |given Map[Set[String], Map[Int, Int]] = null
        |""".stripMargin,
      "given_Map_Set_Map"
    ),
    GivenNameTestData(
      """
        |given Set[Map[Int, Int]] = null
        |""".stripMargin,
      "given_Set_Map"
    ),
    GivenNameTestData(
      """
        |given (Int | Float) = ???
        |""".stripMargin,
      "given_|_Int_Float"
    ),
    GivenNameTestData(
      """
        |given Seq[Int | Float] = ???
        |""".stripMargin,
      "given_Seq_|"
    ),

    //////////////////// Tuples /////////////////
    GivenNameTestData(
      """
        |given (Int, java.lang.String) = null
        |""".stripMargin,
      "given_Int_String"
    ),
    GivenNameTestData(
      """
        |given (Set[Int], Map[Int, Int]) = null
        |""".stripMargin,
      "given_Set_Map"
    ),

    //////////////////// Annonymous Classes /////////////////
    GivenNameTestData(
      """
        |trait A
        |given A with { def close: Unit = () }
        |""".stripMargin,
      "given_A"
    ),
    GivenNameTestData(
      """
        |given java.lang.Throwable with { def close: Unit = () }
        |""".stripMargin,
      "given_Throwable"
    ),
    GivenNameTestData(
      """
        |trait A
        |trait B
        |given (Int, A with B) = null
        |""".stripMargin,
      "given_Int_A"
    ),
    GivenNameTestData(
      """
        |trait A
        |trait B
        |given A with B with {}
        |""".stripMargin,
      "given_A_B"
    ),
    GivenNameTestData(
      """
        |trait A
        |trait B
        |trait C
        |given (A with B with C) = ???
        |""".stripMargin,
      "given__A_B"
    ),
    GivenNameTestData(
      """
        |trait A
        |trait B
        |trait C
        |given ((A with B) with C) = ???
        |""".stripMargin,
      "given__A_C"
    ),

    //////////////////// match types /////////////////
    GivenNameTestData(
      """
        |given [T]: (T match { case Int => String }) = ???
        |""".stripMargin,
      "given_T"
    ),
    GivenNameTestData(
      """
        |given [T]: ((T, Int) match { case (_, Int) => String }) = ???
        |""".stripMargin,
      "given_T_Int"
    ),
    GivenNameTestData(
      """
        |given [T]: (Seq[T] match { case Seq[_] => String }) = ???
        |""".stripMargin,
      "given_Seq_T"
    ),
  )

  case class GivenNameTestData(override val testCode: String, expectedGivenName: String) extends TestData {
    override def testName: String = testCode.trim.linesIterator.toSeq.last.replaceAll(raw"([\s\n])+", " ")
    override def checkCodeFragment: String = {
      s"""
         |object GivenHolder {
         |  ${testCode.trim.replace("\n", "\n  ")}
         |}
         |println(GivenHolder.`$expectedGivenName`)
         |""".stripMargin
    }
  }

  override def makeActualTest(testData: GivenNameTestData): Test =
    new SimpleActualTest(testData, ScalaVersion.Latest.Scala_3) {
      override def runActualTest(): Unit = {
        val GivenNameTestData(code, expectedName) = testData
        val tree = code.parse[ScFile]

        tree.hasParseError shouldBe false

        val givens = tree.elements.collect {
          case given: ScGiven => given
        }.toSeq

        givens.length shouldBe 1

        val givenElement = givens.head
        givenElement.name shouldBe expectedName
      }
    }
}
