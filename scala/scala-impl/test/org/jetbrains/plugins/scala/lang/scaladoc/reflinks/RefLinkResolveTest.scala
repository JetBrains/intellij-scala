package org.jetbrains.plugins.scala.lang.scaladoc.reflinks

import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuery
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class RefLinkResolveTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private case class TestData(queries: Map[ScDocRefQuery, String], markers: Map[String, Seq[Int]], testCode: String, psiFile: PsiFile) {
    private val lines = testCode.linesIterator
      .zipWithIndex
      .map(_.swap)
      .toMap

    def getLine(num: Int): String = lines(num)
  }

  private def makeTestData(testCode: String, expectedLinkCount: Int): TestData = {
    val markers = Seq.newBuilder[(String, Int)]
    val markerRegex = raw"%([^%]*)%".r
    val markerCleanRegex = raw"(\s*%([^%]*)%\s*)*".r
    val cleanedCode = testCode.linesIterator.zipWithIndex.map {
      case (line, lineNum) =>
        for(m <- markerRegex.findAllMatchIn(line)) {
          markers += m.group(1) -> lineNum
        }
        markerCleanRegex.replaceAllIn(line, "")
    }.mkString("\n")


    val file = myFixture.configureByText("dummy.scala", cleanedCode)
    val allQueries = file.depthFirst(e => !e.is[ScDocRefQuery]).filterByType[ScDocRefQuery].toSeq
    if (expectedLinkCount > 0) {
      allQueries.length shouldBe expectedLinkCount
    }
    val queries = allQueries.map(q => q -> q.getParent.nextVisibleLeaf.get.getText).toMap

    val markersMap = markers.result()
      .groupMapReduce(_._1)(x => Seq(x._2))(_ ++ _)
    TestData(queries, markersMap + ("<not found>" -> Seq.empty), cleanedCode, file)
  }

  case class TestResult(resolvedLineNumber: Int, resolvedLine: String)

  private def testQuery(query: ScDocRefQuery, target: String, testData: TestData): Seq[TestResult] = {
    val resolved =
      query.multiResolveScala(incomplete = false)
        .map(_.element.getNavigationElement)
        .map {
          case named: ScNamedElement => named.nameId
          case e => e
        }
    if (resolved == null)
      return Seq.empty


    resolved.toSeq
      .map { r =>
        val offset = r.startOffset
        val num = PsiDocumentManager.getInstance(getProject)
          .getDocument(testData.psiFile)
          .getLineNumber(offset)
        TestResult(num, testData.getLine(num))
      }
  }

  private def checkAll(code: String, expectedLinkCount: Int = -1): Unit = {
    val expectedText = new StringBuilder
    val actualText = new StringBuilder

    val testData = makeTestData(code, expectedLinkCount)

    for ((query, targetMarker) <- testData.queries) {
      def buildLine(ref: String, targetLines: Seq[String]): String = {
        val targets = targetLines match {
          case Seq() => "<not found>"
          case multi => multi.map(_.strip).sorted.distinct.mkString(" | ")
        }
        s"$ref: $targets\n"
      }
      val targetLineNums = testData.markers(targetMarker)
      val targetLines = targetLineNums.map(testData.getLine)
      expectedText ++= buildLine(query.getText, targetLines)

      val actualTargetLine =
        testQuery(query, targetMarker, testData).map(_.resolvedLine)
      actualText ++= buildLine(query.getText, actualTargetLine)
    }

    actualText.toString shouldBe expectedText.toString
  }

  def testUpstreamExample(): Unit = checkAll(
    // also checks these:
    // *  - [[[[Target!.foo[A[_[_]]]*                          trait Target -> def foo with 3 nested tparams]]]] (should exercise nested parens)
    // *  - [[[[Target$$.foo[A[_[_]]]*                         trait Target -> def foo with 3 nested tparams]]]] (should exercise nested parens)
    // *  - [[ImOutside.T#foo                                  class ImOutside#class Inner#method foo]] (check correct interaction between @template and links)
  raw"""
         |package scala.test.scaladoc.links {
         |  import language.higherKinds
         |  class C
         |
         |  trait Target {                                           %trait Target%
         |    type T                                                 %trait Target -> type T%
         |    type S = String                                        %trait Target -> type S%
         |    class C                                                %trait Target -> class C%
         |    def foo(i: Int) = 2                                    %trait Target -> def foo%
         |    def foo(s: String) = 3                                 %trait Target -> def foo%
         |    def foo[A[_]](x: A[String]) = 5                        %trait Target -> def foo%
         |    def foo[A[_[_]]](x: A[List]) = 6                       %trait Target -> def foo%
         |    val bar: Boolean                                       %trait Target -> def bar%
         |    def baz(c: scala.test.scaladoc.links.C) = 7            %trait Target -> def baz%
         |  }
         |
         |  object ExtendsTarget extends Target
         |
         |  object Target {                                          %object Target%
         |    type T = Int => Int                                    %object Target -> type T%
         |    type S = Int                                           %object Target -> type S%
         |    type ::[X] = scala.collection.immutable.::[X]          %object Target -> type ::%
         |    class C                                                %object Target -> class C%
         |    def foo(i: Int) = 2                                    %object Target -> def foo%
         |    def foo(z: String) = 3                                 %object Target -> def foo%
         |    val bar: Boolean = false                               %object Target -> def bar%
         |    val onlyInObject = 1                                   %object Target -> onlyInObject%
         |    def baz(c: scala.test.scaladoc.links.C) = 7            %object Target -> def baz%
         |  }
         |
         |  /**
         |   *  Links to the trait:
         |   *  - [[scala.test.scaladoc.links.Target$$               object Target]]
         |   *  - [[scala.test.scaladoc.links.Target!.T              trait Target -> type T]]
         |   *  - [[test.scaladoc.links.Target!.S                    trait Target -> type S]]
         |   *  - [[scaladoc.links.Target!.foo(i:Int)*               trait Target -> def foo]]
         |   *  - [[links.Target!.bar                                trait Target -> def bar]]
         |   *  - [[Target$$.T                                       object Target -> type T]]
         |   *  - [[Target$$.S                                       object Target -> type S]]
         |   *  - [[Target$$.::                                      object Target -> type ::]]
         |   *  - [[Target$$.foo(z:Str*                              object Target -> def foo]]
         |   *  - [[Target$$.bar                                     object Target -> def bar]]
         |   *  - [[ExtendsTarget.foo(i:Int)*                        trait Target -> def foo]] (disambiguating between inherited members)
         |   *  - [[Target.onlyInObject                              object Target -> onlyInObject]]
         |   *  - [[Target$$.C                                       object Target -> class C]] (should link directly to C, not as a member)
         |   *  - [[Target!.C                                        trait Target -> class C]] (should link directly to C, not as a member)
         |   *  - [[Target$$.baz(c:scala\.test\.scaladoc\.links\.C)* object Target -> def baz]] (should use dots in prefix)
         |   *  - [[Target!.baz(c:scala\.test\.scaladoc\.links\.C)*  trait Target -> def baz]] (should use dots in prefix)
         |   *  - [[localMethod                                      object TEST -> localMethod]] (should use the current template to resolve link instead of inTpl, that's the package)
         |   *  - [[#localMethod                                     object TEST -> localMethod]] (should exercise Java-style links to empty members)
         |   *  - [[ImOutside                                        class ImOutside]] (check correct lookup in EmptyPackage)
         |   *  - [[ImOutside.Inner#foo                              class ImOutside#class Inner#method foo]] (check correct lookup in EmptyPackage)
         |   *  - [[ImOutside.T                                      class ImOutside#type T]] (check correct linking to templates)
         |   */
         |  object TEST {
         |    def localMethod = 3                                    %object TEST -> localMethod%
         |  }
         |}
         |
         |package scala {
         |  trait ImOutside {                                          %class ImOutside%
         |    type T <: Inner                                          %class ImOutside#type T%
         |    class Inner {                                            %class ImOutside#class Inner%
         |      def foo: Any                                           %class ImOutside#class Inner#method foo%
         |    }
         |  }
         |}
         |""".stripMargin,
    expectedLinkCount = 21
  )

  def testHashTagIsLikeDot(): Unit =
    checkAll(
      """object Outer {
        |  class Inner {
        |    def foo = 3 %foo%
        |  }
        |}
        |
        |/**
        | * [[Outer.Inner.foo foo]]
        | * [[Outer.Inner#foo foo]]
        | * [[Outer#Inner.foo foo]]
        | * [[Outer#Inner#foo foo]]
        | */
        |""".stripMargin
    )

  def testForceSimple(): Unit =
    checkAll(
      """object Name {          %object%  %ambiguous%
        |  def foo = "in obj"   %via object%
        |}
        |
        |class Name {           %class%   %ambiguous%
        |  def foo = "in cls"   %via class%
        |}
        |
        |/**
        | * [[Name        ambiguous]]
        | * [[Name!       class]]
        | * [[Name$       object]]
        | * [[Name!.foo   via class]]
        | * [[Name$.foo   via object]]
        | */
        |""".stripMargin
    )

  def testForceTypeInteraction(): Unit =
    checkAll(
      """
        |class !     %class !%
        |object !    %object !%
        |class Id_!  %class Id_!%
        |object Id_! %object Id_!%
        |
        |/**
        | * [[!           <not found>]]
        | * [[!!          class !]]
        | * [[!$          object !]]
        | * [[Id_!        <not found>]]
        | * [[Id_!!       class Id_!]]
        | * [[Id_!$       object Id_!]]
        | */
        |""".stripMargin
    )

  private def checkTypeSelector(code: String): Unit = {
    checkAll(
      s"""
         |object Outer {
         |  $code  %type%
         |}
         |
         |/**
         | * [[Outer.A  type]]
         | * [[Outer.A! type]]
         | * [[Outer.A$$ <not found>]]
         | */
         |""".stripMargin
    )
  }

  def testSelectingTrait(): Unit = checkTypeSelector("trait A")
  def testSelectingClass(): Unit = checkTypeSelector("class A")
  def testSelectingAlias(): Unit = checkTypeSelector("type A = Int")


  private def checkTermSelector(code: String): Unit = {
    checkAll(
      s"""
         |object Outer {
         |  $code  %term%
         |}
         |
         |/**
         | * [[Outer.A  term]]
         | * [[Outer.A$$ term]]
         | * [[Outer.A! <not found>]]
         | */
         |""".stripMargin
    )
  }

  def testSelectingVal(): Unit = checkTermSelector("val A")
  def testSelectingLazyVal(): Unit = checkTermSelector("lazy val A")
  def testSelectingDef(): Unit = checkTermSelector("def A = 0")
  def testSelectingObject(): Unit = checkTermSelector("object A")


  def testSelectingEnum(): Unit =
    checkAll(
      """
        |object Outer {
        |  enum A {          %enum%
        |    case X          %case-without-param%
        |    case Y(y: Int)  %case-with-param%
        |  }
        |}
        |
        |/**
        | * [[Outer.A  enum]]
        | * [[Outer.A! enum]]
        | * [[Outer.A$ enum]]
        | *
        | * [[Outer.A.X  case-without-param]]
        | * [[Outer.A.X! <not found>]]
        | * [[Outer.A.X$ case-without-param]]
        | *
        | * [[Outer.A.Y  case-with-param]]
        | * [[Outer.A.Y! case-with-param]]
        | * [[Outer.A.Y$ case-with-param]]
        | */
        |""".stripMargin
    )

  def testBackslash(): Unit =
    checkAll(
      """
        |object Outer {
        |  type `a.b` = Int   %both%  %type%
        |  val `a.b` = 0      %both%  %term%
        |}
        |
        |/**
        | * [[Outer.`a.b`     both]]
        | * [[Outer.a\.b      both]]
        | * [[Outer.`a\.b`    <not found>]]
        | *
        | * [[Outer.`a.b$`   term]]
        | * [[Outer.a\.b$    term]]
        | * [[Outer.`a\.b$`  <not found>]]
        | *
        | * [[Outer.`a.b!`    type]]
        | * [[Outer.a\.b!     type]]
        | * [[Outer.`a\.b!`   <not found>]]
        | */
        |""".stripMargin

    )

  def testNonNestedOuterPackage(): Unit =
    checkAll(
      """
        |package outer.outer2 {
        |  class InOuter2 %outer2%
        |
        |  packe inner {
        |    /**
        |     * [[InOuter   outer]]
        |     */
        |    class SomeClass
        |  }
        |}
        |
        |package outer {
        |  class InOuter   %outer%
        |}
        |""".stripMargin
    )

  def testStrictMemberId(): Unit = checkAll(
    """
      |package scala {
      |  class Target %inScalaPkg%
      |}
      |/**
      | * [[Target   inScalaPkg]] does a toplevel lookup first
      | * [[#Target  inTest]] only looks into the members
      | */
      |class Test {
      |  object Target %inTest%
      |
      |  /**
      |   * [[#Target  inTest]] strictmember lookup looks into the current class/object not function
      |   */
      |  def foo = 3
      |}
      |
      |/**
      | * [[#Test <not found>]]
      | * [[#Inner <not found>]]
      | */
      |class Inner
      |""".stripMargin
  )

  def testTopLevelSearch(): Unit = checkAll(
    """
      |package topLvl {
      |  object Target /*topLvl*/    %targetTopLvl%
      |}
      |// shouldn't be found because scala.inScala has higher precedence
      |package isScala { object Target }
      |
      |package scala.inScala {
      |  object Target /*inScala*/   %targetInScala%
      |}
      |
      |package outer.inner {
      |  package topLvl { object Target }
      |  package inScala { object Target }
      |
      |  /**
      |   * [[topLvl.Target   targetTopLvl]]
      |   * [[inScala.Target  targetInScala]]
      |   */
      |  class Blub
      |}
      |""".stripMargin
  )

  def testThisQualifier(): Unit = checkAll(
    """
      |/**
      | *  [[this.Target   target]]
      | *  [[this          myself]]
      | *  [[Myself.this   this]]
      | *  [[this.this     this]]
      | *  [[#this         this]]
      | */
      |class Myself {   %myself%
      |  object Target  %target%
      |  object `this`  %this%
      |}
      |""".stripMargin
  )

  def testPackageQualifier(): Unit = checkAll(
    """
      |package org.test
      |
      |object Target %pkg-target%
      |
      |/**
      | *  [[package.Target   pkg-target]]
      | *  [[Myself.package   package]]
      | *  [[#package         package
      | */
      |class Myself {
      |  class Target      %target%
      |  object `package`  %package%
      |}
      |""".stripMargin
  )

  def testObjectResolveInScalaPackage(): Unit = checkAll(
    """
      |package scala
      |
      |class Myself {                        %myself%
      |  /**
      |   * [[Myself.Exception exception]]
      |   * [[Myself           myself]]
      |   */
      |  def func(): Unit = ()
      |}
      |
      |object Myself {                       %myself%
      | class Exception extends Throwable    %exception%
      |}
      |""".stripMargin
  )
}
