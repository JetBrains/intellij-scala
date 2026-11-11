package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.AmbiguousImplicitParameters
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.CommonScalaRevertableChanges
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertEquals

/**
 * The ranking of implicit candidates in Scala 3 (`Applications.compare` in the compiler):
 *  - since source version 3.7, a more general given is preferred over a more specific one,
 *    and the owner decides if one owner derives from the other
 *  - before that, and since 3.7 when both candidates are old-style implicits, a more specific candidate is preferred
 *
 * Each test states the candidate that the compiler picks under the new rules (3.9, `-source:3.7-migration`)
 * and under the old ones (3.6, `-source:3.6`), checked with scalac 3.9.0 and 3.6.4.
 */
abstract class GivenPrioritizationTestBase extends ImplicitParametersTestBase {

  protected def compilerOptions: Seq[String] = Seq.empty

  /** Whether the rules of source version 3.7 apply */
  protected def newRules: Boolean

  override protected def setUp(): Unit = {
    super.setUp()
    if (compilerOptions.nonEmpty) {
      CommonScalaRevertableChanges
        .withCompilerSettingsModified(getModule, s => s.copy(additionalCompilerOptions = s.additionalCompilerOptions ++ compilerOptions))
        .applyChange(this)
    }
  }

  /**
   * @param newRules the candidate picked under the rules of source version 3.7, see [[describe]]
   * @param oldRules the candidate picked under the earlier rules
   */
  protected def doTest(code: String, newRules: String, oldRules: String): Unit = {
    val arguments = getImplicitArguments(code)
    val expected  = if (this.newRules) newRules else oldRules
    assertEquals(expected, arguments.map(describe).mkString("; "))
  }

  protected def doTest(code: String, expected: String): Unit =
    doTest(code, expected, expected)

  /** The name of the picked candidate, `evidence: Bound` for an anonymous context bound, or `ambiguous: names` */
  private def describe(result: ScalaResolveResult): String =
    result.problems match {
      case Seq(AmbiguousImplicitParameters(candidates)) =>
        candidates.map(describe).sorted.mkString("ambiguous: ", ", ", "")
      case Seq()                                        =>
        result.element match {
          case param: ScParameter =>
            ScalaPsiUtil.findSyntheticContextBoundInfo(param)
              .filter(_.name.isEmpty)
              .fold(param.name)(info => s"evidence: ${info.contextType.getText}")
          case element            => element.name
        }
      case problems                                     =>
        problems.mkString("problems: ", ", ", "")
    }

  private val Hierarchy =
    """trait Parent
      |trait Left extends Parent
      |trait Right extends Parent
      |""".stripMargin

  private val TypeClassHierarchy =
    """trait Parent[T]
      |trait Left[T] extends Parent[T]
      |trait Right[T] extends Parent[T]
      |""".stripMargin

  // The example from SCL-26367
  def testUsingParameters(): Unit = doTest(
    s"""$Hierarchy
       |def check(using left: Left, parent: Parent, right: Right): Parent = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "parent",
    oldRules = "ambiguous: left, right"
  )

  def testImplicitParameters(): Unit = doTest(
    s"""$Hierarchy
       |def check(implicit left: Left, parent: Parent, right: Right): Parent = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "ambiguous: left, right"
  )

  def testGivens(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given left: Left = new Left {}
       |  given parent: Parent = new Parent {}
       |  given right: Right = new Right {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "parent",
    oldRules = "ambiguous: left, right"
  )

  def testImplicits(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  implicit val left: Left = new Left {}
       |  implicit val parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  // An implicit and a given: each one is as good as the other, under its own rule
  def testImplicitAndGiven(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  implicit val left: Left = new Left {}
       |  given parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "ambiguous: left, parent",
    oldRules = "left"
  )

  def testGivenAndImplicit(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given left: Left = new Left {}
       |  implicit val parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "ambiguous: left, parent",
    oldRules = "left"
  )

  // Type parameters of the candidate on the left side of a comparison are fixed, those on the right side are inferred
  def testGenericGiven(): Unit = doTest(
    s"""trait Ord[T]
       |object Test:
       |  given intOrd: Ord[Int] = new Ord[Int] {}
       |  given anyOrd[T]: Ord[T] = new Ord[T] {}
       |  val r = ${START}summon[Ord[Int]]$END
       |""".stripMargin,
    expected = "intOrd"
  )

  def testGenericStructuralGiven(): Unit = doTest(
    s"""trait Ord[T]
       |object Test:
       |  given intOrd: Ord[Int] with {}
       |  given anyOrd[T]: Ord[T] with {}
       |  val r = ${START}summon[Ord[Int]]$END
       |""".stripMargin,
    expected = "intOrd"
  )

  def testImplicitAndGenericGiven(): Unit = doTest(
    s"""trait Ord[T]
       |object Test:
       |  implicit val intOrd: Ord[Int] = new Ord[Int] {}
       |  given anyOrd[T]: Ord[T] = new Ord[T] {}
       |  val r = ${START}summon[Ord[Int]]$END
       |""".stripMargin,
    expected = "intOrd"
  )

  def testGivenAndGenericImplicit(): Unit = doTest(
    s"""trait Ord[T]
       |object Test:
       |  given intOrd: Ord[Int] = new Ord[Int] {}
       |  implicit def anyOrd[T]: Ord[T] = new Ord[T] {}
       |  val r = ${START}summon[Ord[Int]]$END
       |""".stripMargin,
    expected = "intOrd"
  )

  // Under the new rules, the owner decides when one owner derives from the other
  def testDerivedOwnerWithSpecificGiven(): Unit = doTest(
    s"""$Hierarchy
       |trait LowPriority:
       |  given parent: Parent = new Parent {}
       |object Test extends LowPriority:
       |  given left: Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  def testDerivedOwnerWithGeneralGiven(): Unit = doTest(
    s"""$Hierarchy
       |trait LowPriority:
       |  given left: Left = new Left {}
       |object Test extends LowPriority:
       |  given parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "parent",
    oldRules = "ambiguous: left, parent"
  )

  def testDerivedOwnerWithGivenOverImplicit(): Unit = doTest(
    s"""$Hierarchy
       |trait LowPriority:
       |  implicit val left: Left = new Left {}
       |object Test extends LowPriority:
       |  given parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "parent",
    oldRules = "ambiguous: left, parent"
  )

  def testDerivedOwnerWithSpecificImplicit(): Unit = doTest(
    s"""$Hierarchy
       |trait LowPriority:
       |  implicit val parent: Parent = new Parent {}
       |object Test extends LowPriority:
       |  implicit val left: Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  // For two implicits, an owner and a type that disagree are a draw under any rules
  def testDerivedOwnerWithGeneralImplicit(): Unit = doTest(
    s"""$Hierarchy
       |trait LowPriority:
       |  implicit val left: Left = new Left {}
       |object Test extends LowPriority:
       |  implicit val parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "ambiguous: left, parent"
  )

  def testNestingOverGenerality(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given parent: Parent = new Parent {}
       |  def r =
       |    given left: Left = new Left {}
       |    ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  // The candidate without implicit parameters wins only if the types do not decide
  def testWithoutImplicitParameters(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given Int = 0
       |  given withUsing(using Int): Parent = new Parent {}
       |  given plain: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "plain"
  )

  def testWithImplicitParameters(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given Int = 0
       |  given withUsing(using Int): Parent = new Parent {}
       |  given plain: Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "withUsing",
    oldRules = "plain"
  )

  // Both with implicit parameters: the types without the implicit clauses decide first, then the implicit parameters
  // are compared like the parameters of overloads, under any rules
  def testBothWithImplicitParametersOfUnrelatedTypes(): Unit = doTest(
    s"""$Hierarchy
       |given Int = 0
       |given String = ""
       |object Test:
       |  given a(using Int): Parent = new Parent {}
       |  given b(using String): Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "ambiguous: a, b"
  )

  def testBothWithImplicitParametersOfRelatedTypes(): Unit = doTest(
    s"""$Hierarchy
       |given Int = 0
       |object Test:
       |  given a(using Int): Parent = new Parent {}
       |  given b(using AnyVal): Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "a"
  )

  def testBothWithImplicitParametersAndRelatedTypes(): Unit = doTest(
    s"""$Hierarchy
       |given Int = 0
       |object Test:
       |  given a(using Int): Parent = new Parent {}
       |  given b(using Int): Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "a",
    oldRules = "b"
  )

  def testGenericAndSpecificWithImplicitParameters(): Unit = doTest(
    s"""trait Ord[T]
       |given Ord[Int] = new Ord[Int] {}
       |object Test:
       |  given listOrd[T](using Ord[T]): Ord[List[T]] = new Ord[List[T]] {}
       |  given intListOrd(using Ord[Int]): Ord[List[Int]] = new Ord[List[Int]] {}
       |  val r = ${START}summon[Ord[List[Int]]]$END
       |""".stripMargin,
    expected = "intListOrd"
  )

  // A value is not automatically as good as a given alias or a method without explicit parameters
  def testClassParameterAndGivenAlias(): Unit = doTest(
    s"""$Hierarchy
       |class Test(using p: Parent):
       |  given left: Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "p",
    oldRules = "left"
  )

  def testClassImplicitParameterAndImplicitDef(): Unit = doTest(
    s"""$Hierarchy
       |class Test(implicit p: Parent):
       |  implicit def left: Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  def testImplicitValAndImplicitDef(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  implicit val parent: Parent = new Parent {}
       |  implicit def left: Left = new Left {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  def testImplicitDefAndImplicitVal(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  implicit val left: Left = new Left {}
       |  implicit def parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    expected = "left"
  )

  // Structural givens are compared by their parents
  def testStructuralGivenAndAliasOfSameType(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given g1: Left with {}
       |  given g2: Left = new Left {}
       |  val r = ${START}summon[Left]$END
       |""".stripMargin,
    expected = "ambiguous: g1, g2"
  )

  def testStructuralGivenAndGeneralAlias(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given left: Left with {}
       |  given parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "parent",
    oldRules = "left"
  )

  def testStructuralGivenAndImplicit(): Unit = doTest(
    s"""$Hierarchy
       |object Test:
       |  given left: Left with {}
       |  implicit val parent: Parent = new Parent {}
       |  val r = ${START}summon[Parent]$END
       |""".stripMargin,
    newRules = "ambiguous: left, parent",
    oldRules = "left"
  )

  def testGenericStructuralGivenAndAliasOfSameType(): Unit = doTest(
    s"""trait Ord[T]
       |object Test:
       |  given o1[T]: Ord[List[T]] with {}
       |  given o2[T]: Ord[List[T]] = new Ord[List[T]] {}
       |  val r = ${START}summon[Ord[List[Int]]]$END
       |""".stripMargin,
    expected = "ambiguous: o1, o2"
  )

  def testGenericStructuralGivenAndSpecificAlias(): Unit = doTest(
    s"""trait Ord[T]
       |object Test:
       |  given o1[T]: Ord[List[T]] with {}
       |  given o2: Ord[List[Int]] = new Ord[List[Int]] {}
       |  val r = ${START}summon[Ord[List[Int]]]$END
       |""".stripMargin,
    expected = "o2"
  )

  // Context bound evidence takes the kind of the clause it is merged into
  def testContextBoundsMergedIntoUsingClause(): Unit = doTest(
    s"""$TypeClassHierarchy
       |def check[T: Left: Right](using p: Parent[T]): Parent[T] = ${START}summon[Parent[T]]$END
       |""".stripMargin,
    newRules = "p",
    oldRules = "ambiguous: evidence: Left, evidence: Right"
  )

  def testContextBoundsMergedIntoImplicitClause(): Unit = doTest(
    s"""$TypeClassHierarchy
       |def check[T: Left: Right](implicit p: Parent[T]): Parent[T] = ${START}summon[Parent[T]]$END
       |""".stripMargin,
    expected = "ambiguous: evidence: Left, evidence: Right"
  )

  def testContextBoundMergedIntoUsingClause(): Unit = doTest(
    s"""$TypeClassHierarchy
       |def check[T: Left](using p: Parent[T]): Parent[T] = ${START}summon[Parent[T]]$END
       |""".stripMargin,
    newRules = "p",
    oldRules = "evidence: Left"
  )

  def testContextBoundMergedIntoImplicitClause(): Unit = doTest(
    s"""$TypeClassHierarchy
       |def check[T: Left](implicit p: Parent[T]): Parent[T] = ${START}summon[Parent[T]]$END
       |""".stripMargin,
    expected = "evidence: Left"
  )

  def testContextBounds(): Unit = doTest(
    s"""$TypeClassHierarchy
       |def check[T: Left: Parent]: Parent[T] = ${START}summon[Parent[T]]$END
       |""".stripMargin,
    newRules = "evidence: Parent",
    oldRules = "evidence: Left"
  )

  def testNamedContextBounds(): Unit = doTest(
    s"""$TypeClassHierarchy
       |def check[T: Left as l: Parent as p]: Parent[T] = ${START}summon[Parent[T]]$END
       |""".stripMargin,
    newRules = "p",
    oldRules = "l"
  )

  // Overloading resolution still prefers the most specific alternative
  def testOverloadingResolution(): Unit = checkTextHasNoErrors(
    s"""$Hierarchy
       |object Test:
       |  def foo(x: Parent): Int = 1
       |  def foo(x: Left): String = ""
       |  val r: String = foo(new Left {})
       |""".stripMargin
  )
}

class GivenPrioritizationTest_3_6 extends GivenPrioritizationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_6

  override protected def newRules: Boolean = false
}

class GivenPrioritizationTest_3_9 extends GivenPrioritizationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def newRules: Boolean = true
}

class GivenPrioritizationTest_3_9_Source3_6 extends GivenPrioritizationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def compilerOptions: Seq[String] = Seq("-source:3.6")

  override protected def newRules: Boolean = false
}

class GivenPrioritizationTest_3_9_Source3_7Migration extends GivenPrioritizationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def compilerOptions: Seq[String] = Seq("-source:3.7-migration")

  override protected def newRules: Boolean = true
}
