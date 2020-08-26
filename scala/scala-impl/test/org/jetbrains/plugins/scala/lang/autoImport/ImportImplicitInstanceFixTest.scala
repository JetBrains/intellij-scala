package org.jetbrains.plugins.scala.lang.autoImport

import org.jetbrains.plugins.scala.autoImport.quickFix.ImportImplicitInstanceFix
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ImportImplicitInstanceFixTest extends ImportElementFixTestBase[ImplicitArgumentsOwner] {
  private def findImplicitArgs(owner: ImplicitArgumentsOwner): Option[collection.Seq[ScalaResolveResult]] =
    owner.findImplicitArguments match {
      case None => owner.parentOfType[ImplicitArgumentsOwner].flatMap(findImplicitArgs)
      case args => args
    }

  override def createFix(element: ImplicitArgumentsOwner) = {
    for {
      args <- findImplicitArgs(element)
      notFound = args.filter(_.isNotFoundImplicitParameter)
      fix <- ImportImplicitInstanceFix(notFound, element)
    } yield fix
  }

  def testExecutionContext(): Unit = checkElementsToImport(
    s"""
       |import scala.concurrent.Future
       |
       |object Test {
       |  ${CARET}Future(42)
       |}
       |""".stripMargin,

    "scala.concurrent.ExecutionContext.Implicits.global"
  )

  def testSeqDerivedOrdering(): Unit = checkElementsToImport(
    s"""
       |object Test {
       |  Seq(Seq(1)).${CARET}sorted
       |}
       |""".stripMargin,

    "scala.math.Ordering.Implicits.seqDerivedOrdering"
  )

  def testNoOrderingForCustomClass(): Unit = checkNoImportFix(
    s"""
       |class MyClass
       |object Test {
       |  Seq(new MyClass).${CARET}sorted
       |}
       |""".stripMargin
  )

  def testDerivation(): Unit = checkElementsToImport(
    s"""
       |class MyClass
       |object implicits {
       |  implicit val myClassComparator: Comparator[MyClass] = ???
       |  implicit val myClassOrdering: Ordering[MyClass] = ???
       |}
       |object Test {
       |  Seq(new MyClass).${CARET}sorted
       |}
       |""".stripMargin,

    "implicits.myClassOrdering",
    "implicits.myClassComparator"
  )

  def testNotSuggestVisible(): Unit = checkElementsToImport(
    s"""
       |import scala.math.Ordering.Implicits.seqDerivedOrdering
       |
       |class MyClass
       |object implicits {
       |  implicit val myClassComparator: Comparator[MyClass] = ???
       |  implicit val myClassOrdering: Ordering[MyClass] = ???
       |}
       |object Test {
       |  Seq(Seq(new MyClass)).${CARET}sorted
       |}
       |""".stripMargin,

    "implicits.myClassOrdering",
    "implicits.myClassComparator"
  )


  def testTwoArguments(): Unit = checkElementsToImport(
    s"""
       |class A; class B
       |object implicits {
       |  implicit object myA extends A
       |  implicit def myB: B = ???
       |}
       |object Test {
       |  def foo(implicit a: A, b: B) = ???
       |
       |  ${CARET}foo
       |}""".stripMargin,

    "implicits.myA",
    "implicits.myB"
  )

  def testGenericImplicit(): Unit = checkElementsToImport(
    s"""
       |trait Foo[T]
       |
       |object implicits {
       |  implicit object StringFoo extends Foo[String]
       |  implicit val intFoo: Foo[Int] = ???
       |  implicit def optionFoo[T]: Foo[Option[T]] = ???
       |}
       |
       |object Test {
       |  def bar[T](implicit foo: Foo[T]) = ???
       |
       |  ${CARET}bar
       |}
       |""".stripMargin,

    "implicits.StringFoo",
    "implicits.intFoo",
    "implicits.optionFoo"
  )

  def testNoCompatible(): Unit = checkNoImportFix(
    s"""
       |trait Foo[T]
       |
       |object implicits {
       |  implicit object StringFoo extends Foo[String]
       |  implicit val infFoo: Foo[Int] = ???
       |}
       |
       |object Test {
       |  def bar(implicit foo: Foo[Boolean]) = ???
       |
       |  ${CARET}bar
       |}
       |""".stripMargin)

  def testImportFromVal(): Unit = checkElementsToImport(
    s"""
       |trait A
       |trait Owner {
       |
       |  trait API {
       |    implicit val implicitA: A = ???
       |  }
       |  val api: API = ???
       |}
       |
       |object OwnerImpl extends Owner
       |
       |object Test {
       |  ${CARET}implicitly[A]
       |}""".stripMargin,

    "OwnerImpl.api.implicitA"
  )

}