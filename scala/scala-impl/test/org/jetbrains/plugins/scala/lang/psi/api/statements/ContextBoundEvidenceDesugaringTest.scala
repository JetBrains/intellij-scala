package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGivenDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, TypePresentationContext}
import org.jetbrains.plugins.scala.util.CommonScalaRevertableChanges
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertEquals

/**
 * Tests the placement and the kind (`implicit` or `using`) of the evidence parameters of context bounds.
 *
 * The expected signatures are the ones that the compiler produces (`-Vprint:typer`), with the following changes:
 *  - evidence parameters of anonymous context bounds are named `ev`
 *  - like in the compiler output, the first parameter defines the clause kind and the following
 *    `implicit` parameters are marked as such, e.g. `(using ev: Show[A], implicit y: Int)`
 *  - extension methods show the clauses of the extension, then the clauses of the method
 *  - primary constructors show the empty clause that the compiler adds: in front of a leading `implicit` clause,
 *    e.g. `C4[A]()(implicit ev: Show[A])`, or after `using` clauses, e.g. `C4[A](using ev: Show[A])()`
 *
 * Known differences with the compiler are marked with `NOTE`.
 */
abstract class ContextBoundEvidenceDesugaringTestBase extends ScalaFixtureTestCase {

  protected def compilerOptions: Seq[String] = Seq.empty

  override protected def setUp(): Unit = {
    super.setUp()
    if (compilerOptions.nonEmpty) {
      CommonScalaRevertableChanges
        .withCompilerSettingsModified(myModule, s =>s.copy(additionalCompilerOptions = s.additionalCompilerOptions ++ compilerOptions))
        .applyChange(this)
    }
  }

  protected def doTest(code: String, expected: String): Unit = {
    val file = myFixture.configureByText(s"${getTestName(false)}.scala", code)
    assertEquals(expected.trim, describe(file))
  }

  private def describe(file: PsiFile): String =
    file.depthFirst().flatMap {
      case cls: ScClass =>
        cls.constructor.map { constructor =>
          s"class ${cls.name}${typeParameters(cls.typeParameters.map(_.name))}${clauses(constructor.effectiveSignatureClauses)}"
        }
      case givenDefinition: ScGivenDefinition =>
        givenDefinition.desugaredDefinitions.collectFirst { case function: ScFunction => describe(function) }
      case function: ScFunction => Option(describe(function))
      case _                    => None
    }.mkString("\n")

  private def describe(function: ScFunction): String = {
    val extension = function.extensionMethodOwner.fold("")(e => s"extension ${clauses(e.signatureClauses)} ")
    val name      = if (function.isConstructor) "this" else function.name
    s"${extension}def $name${clauses(function.effectiveSignatureClauses)}"
  }

  private def clauses(signatureClauses: Seq[ScSignatureClause]): String =
    signatureClauses.map {
      case ScSignatureClause.TypeClause(clause) => typeParameters(clause.typeParameters.map(_.name))
      case ScSignatureClause.TermClause(clause) => parameters(clause.effectiveParameters)
    }.mkString

  private def typeParameters(names: Seq[String]): String =
    if (names.isEmpty) "" else names.mkString("[", ", ", "]")

  private def parameters(parameters: Seq[ScParameter]): String =
    parameters.zipWithIndex.map { case (parameter, idx) =>
      val kind =
        if (parameter.isInClauseWithImplicit) Option("implicit")
        else if (parameter.isInClauseWithUsing || parameter.isGivenConditional) Option("using")
        else None

      val prefix =
        if (idx == 0) kind.fold("")(_ + " ")
        else if (kind.contains("implicit")) "implicit "
        else ""

      val name = if (ScalaPsiUtil.isAnonymousContextBoundEvidence(parameter)) "ev" else parameter.name

      // the type as written for physical parameters, the synthetic ones have types like `(Show)[A]`
      val tpe =
        if (parameter.isPhysical) parameter.typeElement.fold("")(_.getText)
        else parameter.`type`().getOrAny.presentableText(using TypePresentationContext(parameter), Context(parameter))

      s"$prefix$name: $tpe"
    }.mkString("(", ", ", ")")
}

object ContextBoundEvidenceDesugaringTestBase {

  /** Valid in all Scala 3 versions */
  val BasicCode: String =
    """trait Show[A]
      |trait Ord[A]
      |
      |object Defs:
      |  def d1[A: Show: Ord](x: A) = ()
      |  def d2[A: Show, B: Ord](x: A)(using y: Int) = ()
      |  def d3[A: Show](using y: Int)(x: A) = ()
      |  def d4[A: Show](implicit y: Int) = ()
      |  def d5[A: Show]() = ()
      |  def d6[A: Show](using z: Int)(y: Int)(using w: Long) = ()
      |
      |class C1[A: Show](x: A)(using y: Int)
      |class C2[A: Show](using y: Int)(x: A)
      |class C3[A: Show](x: A):
      |  def this(x: A, y: Int) = this(x)
      |class C4[A: Show]
      |class C5[A: Show](implicit y: Int):
      |  def this(x: Int, z: Long) = this()(using ???, x)
      |class C6[A: Show](x: A):
      |  def this(x: A, z: Long)(using w: Int) = this(x)
      |
      |object Extensions:
      |  extension [A: Show](x: A) def e1[B: Ord](y: B): Unit = ()
      |  extension [A: Show](x: A)(using i: Int) def e2[B: Ord](y: B): Unit = ()
      |  extension [A](x: A) def e3[B: Ord](y: B)(using l: Long): Unit = ()
      |  extension [A: Show](x: A) def e4(using l: Long): Unit = ()
      |  extension [A: Show](x: A)(using i: Int) def e5: Unit = ()
      |  extension [A: Show](x: A) def e6: Unit = ()
      |
      |object Givens:
      |  given g1[A: Show]: Ord[List[A]] = new Ord[List[A]] {}
      |  given g2[A: Show]: Ord[Option[A]] with {}
      |""".stripMargin

  /** Interleaved type parameter clauses, named context bounds and the new given syntax, valid since Scala 3.6 */
  val Scala3_6Code: String =
    """trait Show[A] { type Out }
      |trait Ord[A]
      |
      |object Interleaved:
      |  def i1[A: Show](x: A)[B: Ord](y: B) = ()
      |  def i2[A: Show](using x: Int)[B: Ord](y: B) = ()
      |  def i3[A: Show](x: A)[B: Ord](using y: B) = ()
      |  def i4[A: Show](x: A)[B: Ord](implicit y: B) = ()
      |  def i5[A](x: A)[B: Ord](y: B) = ()
      |  def i6[A: Show](x: A)[B](y: B) = ()
      |  def i7[A: Show as s](x: s.Out)[B: Ord](y: B) = ()
      |  def i8[A: Show](x: A)[B: Ord as o](y: o.type) = ()
      |  def i9[A: {Show, Ord}](x: A)[B: Ord](y: B)(using z: Int) = ()
      |
      |object Named:
      |  def n1[A: Show as s, B: Ord](x: A) = ()
      |  def n2[A: Show as s](x: s.Out)(y: Int) = ()
      |  def n3[A: Show as s](x: Int)(y: s.Out)(using z: Int) = ()
      |  def n4[A: Show as s](x: Int)[B <: s.Out](y: B) = ()
      |
      |class C7[A: Show as s](x: s.Out)
      |
      |object NewGivens:
      |  given g3: [A: Show] => Ord[Vector[A]] = new Ord[Vector[A]] {}
      |  given g4: [A: Show] => (x: Ord[A]) => Ord[Seq[A]] = new Ord[Seq[A]] {}
      |""".stripMargin

  /**
   * Since Scala 3.6. The compiler rejects `i7` and `n4`: in `i7`, the evidence for `B` precedes the declaration of `B`
   * (it's placed before the first usage of `s`); in `n4`, the usage of `s` in a type clause doesn't count.
   */
  val Scala3_6Expected: String =
    """def i1[A](x: A)[B](y: B)(using ev: Show[A], ev: Ord[B])
      |def i2[A](using x: Int)[B](y: B)(using ev: Show[A], ev: Ord[B])
      |def i3[A](x: A)[B](using ev: Show[A], ev: Ord[B], y: B)
      |def i4[A](x: A)[B](implicit ev: Show[A], implicit ev: Ord[B], implicit y: B)
      |def i5[A](x: A)[B](y: B)(using ev: Ord[B])
      |def i6[A](x: A)[B](y: B)(using ev: Show[A])
      |def i7[A](using s: Show[A], ev: Ord[B])(x: s.Out)[B](y: B)
      |def i8[A](x: A)[B](using ev: Show[A], o: Ord[B])(y: o.type)
      |def i9[A](x: A)[B](y: B)(using ev: Show[A], ev: Ord[A], ev: Ord[B], z: Int)
      |def n1[A, B](x: A)(using s: Show[A], ev: Ord[B])
      |def n2[A](using s: Show[A])(x: s.Out)(y: Int)
      |def n3[A](x: Int)(using s: Show[A])(y: s.Out)(using z: Int)
      |def n4[A](x: Int)[B](y: B)(using s: Show[A])
      |class C7[A](using s: Show[A])(x: s.Out)
      |def g3[A](using ev: Show[A])
      |def g4[A](using ev: Show[A], x: Ord[A])
      |""".stripMargin
}

import org.jetbrains.plugins.scala.lang.psi.api.statements.ContextBoundEvidenceDesugaringTestBase._

/** Before Scala 3.6, evidence is `implicit`, even when it is merged into a `using` clause */
class ContextBoundEvidenceDesugaringTest_3_3 extends ContextBoundEvidenceDesugaringTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  def testBasic(): Unit = doTest(BasicCode,
    """def d1[A](x: A)(implicit ev: Show[A], implicit ev: Ord[A])
      |def d2[A, B](x: A)(implicit ev: Show[A], implicit ev: Ord[B], y: Int)
      |def d3[A](using y: Int)(x: A)(implicit ev: Show[A])
      |def d4[A](implicit ev: Show[A], implicit y: Int)
      |def d5[A]()(implicit ev: Show[A])
      |def d6[A](using z: Int)(y: Int)(implicit ev: Show[A], w: Long)
      |class C1[A](x: A)(implicit ev: Show[A], y: Int)
      |class C2[A](using y: Int)(x: A)(implicit ev: Show[A])
      |class C3[A](x: A)(implicit ev: Show[A])
      |def this(x: A, y: Int)(implicit ev: Show[A])
      |class C4[A]()(implicit ev: Show[A])
      |class C5[A]()(implicit ev: Show[A], implicit y: Int)
      |def this(x: Int, z: Long)(implicit ev: Show[A])
      |class C6[A](x: A)(implicit ev: Show[A])
      |def this(x: A, z: Long)(implicit ev: Show[A], w: Int)
      |extension [A](x: A) def e1[B](y: B)(implicit ev: Show[A], implicit ev: Ord[B])
      |extension [A](x: A)(using i: Int) def e2[B](y: B)(implicit ev: Show[A], implicit ev: Ord[B])
      |extension [A](x: A) def e3[B](y: B)(implicit ev: Ord[B], l: Long)
      |extension [A](x: A) def e4(implicit ev: Show[A], l: Long)
      |extension [A](x: A)(using i: Int) def e5(implicit ev: Show[A])
      |extension [A](x: A) def e6(implicit ev: Show[A])
      |def g1[A](implicit ev: Show[A])
      |def g2[A](implicit ev: Show[A])
      |""".stripMargin
    // NOTE: for e5, the compiler merges the evidence into the extension clause: `(implicit ev: Show[A], i: Int)`
  )
}

/** Before Scala 3.6, evidence is `using` only with `-source:future`, even when it is merged into an `implicit` clause */
class ContextBoundEvidenceDesugaringTest_3_3_SourceFuture extends ContextBoundEvidenceDesugaringTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  override protected def compilerOptions: Seq[String] = Seq("-source:future")

  def testBasic(): Unit = doTest(BasicCode,
    """def d1[A](x: A)(using ev: Show[A], ev: Ord[A])
      |def d2[A, B](x: A)(using ev: Show[A], ev: Ord[B], y: Int)
      |def d3[A](using y: Int)(x: A)(using ev: Show[A])
      |def d4[A](using ev: Show[A], implicit y: Int)
      |def d5[A]()(using ev: Show[A])
      |def d6[A](using z: Int)(y: Int)(using ev: Show[A], w: Long)
      |class C1[A](x: A)(using ev: Show[A], y: Int)
      |class C2[A](using y: Int)(x: A)(using ev: Show[A])
      |class C3[A](x: A)(using ev: Show[A])
      |def this(x: A, y: Int)(using ev: Show[A])
      |class C4[A](using ev: Show[A])()
      |class C5[A](using ev: Show[A], implicit y: Int)()
      |def this(x: Int, z: Long)(using ev: Show[A])
      |class C6[A](x: A)(using ev: Show[A])
      |def this(x: A, z: Long)(using ev: Show[A], w: Int)
      |extension [A](x: A) def e1[B](y: B)(using ev: Show[A], ev: Ord[B])
      |extension [A](x: A)(using i: Int) def e2[B](y: B)(using ev: Show[A], ev: Ord[B])
      |extension [A](x: A) def e3[B](y: B)(using ev: Ord[B], l: Long)
      |extension [A](x: A) def e4(using ev: Show[A], l: Long)
      |extension [A](x: A)(using i: Int) def e5(using ev: Show[A])
      |extension [A](x: A) def e6(using ev: Show[A])
      |def g1[A](using ev: Show[A])
      |def g2[A](using ev: Show[A])
      |""".stripMargin
    // NOTE: for e5, the compiler merges the evidence into the extension clause: `(using ev: Show[A], i: Int)`
  )
}

class ContextBoundEvidenceDesugaringTest_3_5 extends ContextBoundEvidenceDesugaringTest_3_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_5
}

/**
 * Since Scala 3.6, evidence takes the kind of the last clause if it is an `implicit` or `using` clause
 * (it is merged into the clause), otherwise it is `using` evidence since source version 3.6
 */
class ContextBoundEvidenceDesugaringTest_3_6 extends ContextBoundEvidenceDesugaringTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_6

  def testBasic(): Unit = doTest(BasicCode,
    """def d1[A](x: A)(using ev: Show[A], ev: Ord[A])
      |def d2[A, B](x: A)(using ev: Show[A], ev: Ord[B], y: Int)
      |def d3[A](using y: Int)(x: A)(using ev: Show[A])
      |def d4[A](implicit ev: Show[A], implicit y: Int)
      |def d5[A]()(using ev: Show[A])
      |def d6[A](using z: Int)(y: Int)(using ev: Show[A], w: Long)
      |class C1[A](x: A)(using ev: Show[A], y: Int)
      |class C2[A](using y: Int)(x: A)(using ev: Show[A])
      |class C3[A](x: A)(using ev: Show[A])
      |def this(x: A, y: Int)(using ev: Show[A])
      |class C4[A](using ev: Show[A])()
      |class C5[A]()(implicit ev: Show[A], implicit y: Int)
      |def this(x: Int, z: Long)(implicit ev: Show[A])
      |class C6[A](x: A)(using ev: Show[A])
      |def this(x: A, z: Long)(using ev: Show[A], w: Int)
      |extension [A](x: A) def e1[B](y: B)(using ev: Show[A], ev: Ord[B])
      |extension [A](x: A)(using i: Int) def e2[B](y: B)(using ev: Show[A], ev: Ord[B])
      |extension [A](x: A) def e3[B](y: B)(using ev: Ord[B], l: Long)
      |extension [A](x: A) def e4(using ev: Show[A], l: Long)
      |extension [A](x: A)(using i: Int) def e5(using ev: Show[A])
      |extension [A](x: A) def e6(using ev: Show[A])
      |def g1[A](using ev: Show[A])
      |def g2[A](using ev: Show[A])
      |""".stripMargin
    // NOTE: for e5, the compiler merges the evidence into the extension clause: `(using ev: Show[A], i: Int)`
  )

  def testScala3_6(): Unit = doTest(Scala3_6Code, Scala3_6Expected)
}

/** With `-source:3.5`, evidence that is not merged into a clause is `implicit` */
class ContextBoundEvidenceDesugaringTest_3_6_Source3_5 extends ContextBoundEvidenceDesugaringTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_6

  override protected def compilerOptions: Seq[String] = Seq("-source", "3.5")

  def testBasic(): Unit = doTest(BasicCode,
    """def d1[A](x: A)(implicit ev: Show[A], implicit ev: Ord[A])
      |def d2[A, B](x: A)(using ev: Show[A], ev: Ord[B], y: Int)
      |def d3[A](using y: Int)(x: A)(implicit ev: Show[A])
      |def d4[A](implicit ev: Show[A], implicit y: Int)
      |def d5[A]()(implicit ev: Show[A])
      |def d6[A](using z: Int)(y: Int)(using ev: Show[A], w: Long)
      |class C1[A](x: A)(using ev: Show[A], y: Int)
      |class C2[A](using y: Int)(x: A)(implicit ev: Show[A])
      |class C3[A](x: A)(implicit ev: Show[A])
      |def this(x: A, y: Int)(implicit ev: Show[A])
      |class C4[A]()(implicit ev: Show[A])
      |class C5[A]()(implicit ev: Show[A], implicit y: Int)
      |def this(x: Int, z: Long)(implicit ev: Show[A])
      |class C6[A](x: A)(implicit ev: Show[A])
      |def this(x: A, z: Long)(using ev: Show[A], w: Int)
      |extension [A](x: A) def e1[B](y: B)(implicit ev: Show[A], implicit ev: Ord[B])
      |extension [A](x: A)(using i: Int) def e2[B](y: B)(implicit ev: Show[A], implicit ev: Ord[B])
      |extension [A](x: A) def e3[B](y: B)(using ev: Ord[B], l: Long)
      |extension [A](x: A) def e4(using ev: Show[A], l: Long)
      |extension [A](x: A)(using i: Int) def e5(using ev: Show[A])
      |extension [A](x: A) def e6(implicit ev: Show[A])
      |def g1[A](implicit ev: Show[A])
      |def g2[A](implicit ev: Show[A])
      |""".stripMargin
    // NOTE: for e5, the compiler merges the evidence into the extension clause: `(using ev: Show[A], i: Int)`
  )
}

class ContextBoundEvidenceDesugaringTest_3_9 extends ContextBoundEvidenceDesugaringTest_3_6 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9
}

/** A migration source version is treated like its stable version */
class ContextBoundEvidenceDesugaringTest_3_9_Source3_6Migration extends ContextBoundEvidenceDesugaringTest_3_6 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def compilerOptions: Seq[String] = Seq("-source:3.6-migration")
}
