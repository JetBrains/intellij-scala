package org.jetbrains.plugins.scala.externalLibraries.derevo

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class DerevoSimpleTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  == LatestScalaVersions.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()

    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val settings       = defaultProfile.getSettings

    val newSettings = settings.copy(
      plugins = settings.plugins :+ "kind-projector"
    )

    defaultProfile.setSettings(newSettings)
  }

  override def additionalLibraries: Seq[LibraryLoader] = Seq(IvyManagedLoader("tf.tofu" %% "derevo-core" % "0.13.0"))

  def testFailing(): Unit = checkHasErrorAroundCaret(
    s"""
      |
      |import derevo._
      |
      |trait TypeClass[A]
      |
      |object TypeClass extends Derivation[TypeClass] {
      |  def instance[A]: TypeClass[A] = ???
      |}
      |
      |case class Target(i: Int)
      |
      |object Test {
      |  implici${CARET}tly[TypeClass[Target]]
      |}
      |""".stripMargin
  )

  def testNoArg(): Unit = checkTextHasNoErrors(
    """
      |import derevo._
      |
      |trait TypeClass[A]
      |
      |object TypeClass extends Derivation[TypeClass] {
      |  def instance[A]: TypeClass[A] = ???
      |}
      |
      |@derive(TypeClass)
      |case class Target(i: Int)
      |
      |object Test {
      |  implicitly[TypeClass[Target]]
      |}
      |""".stripMargin
  )

  def testOneArgWithoutTcImpl(): Unit = checkHasErrorAroundCaret(
    s"""
      |import derevo._
      |
      |trait TypeClass[A]
      |
      |object TypeClass extends Derivation[TypeClass] {
      |  def instance[A]: TypeClass[A] = ???
      |}
      |
      |@derive(TypeClass)
      |class Target[A]
      |
      |class Arg
      |
      |object Test {
      |  implicit${CARET}ly[TypeClass[Target[Arg]]]
      |}
      |""".stripMargin
  )

  def testOneArgWithTcImpl(): Unit = checkTextHasNoErrors(
    s"""
       |import derevo._
       |
       |trait TypeClass[A]
       |
       |object TypeClass extends Derivation[TypeClass] {
       |  def instance[A]: TypeClass[A] = ???
       |}
       |
       |@derive(TypeClass)
       |class Target[A]
       |
       |@derive(TypeClass)
       |class Arg
       |
       |object Test {
       |  implicitly[TypeClass[Target[Arg]]]
       |}
       |""".stripMargin
  )

  def testPhantom(): Unit = checkTextHasNoErrors(
    s"""
       |import derevo._
       |
       |trait TypeClass[A]
       |
       |object TypeClass extends Derivation[TypeClass] {
       |  def instance[A]: TypeClass[A] = ???
       |}
       |
       |@derive(TypeClass)
       |class Target[@phantom A]
       |
       |class Arg
       |
       |object Test {
       |  implicitly[TypeClass[Target[Arg]]]
       |}
       |""".stripMargin
  )

  def testFuncs(): Unit = checkTextHasNoErrors(
    s"""
       |import derevo._
       |
       |trait TypeClass[A]
       |
       |object TypeClass extends Derivation[TypeClass] {
       |  def apply[A](param: Int): TypeClass[A] = ???
       |  def blub[A](param: Int): TypeClass[A] = ???
       |}
       |
       |@derive(TypeClass(3))
       |class Target
       |
       |@derive(TypeClass.blub(3))
       |class Target2
       |
       |object Test {
       |  implicitly[TypeClass[Target]]
       |  implicitly[TypeClass[Target2]]
       |}
       |""".stripMargin
  )

  def testComposite(): Unit = checkTextHasNoErrors(
    s"""
       |import derevo._
       |
       |trait TypeClass1[A]
       |trait TypeClass2[A]
       |
       |object TypeClass1 extends Derivation[TypeClass1] {
       |  def instance[A]: TypeClass1[A] = ???
       |}
       |
       |object TypeClass2 extends Derivation[TypeClass2] {
       |  def instance[A]: TypeClass2[A] = ???
       |}
       |
       |@composite(TypeClass1, TypeClass2)
       |object BothTypeClasses extends CompositeDerivation
       |
       |@derive(BothTypeClasses)
       |class Target
       |
       |object Test {
       |  implicitly[TypeClass1[Target]]
       |  implicitly[TypeClass2[Target]]
       |}
       |""".stripMargin
  )

  def testKindProjector(): Unit = checkTextHasNoErrors(
    """
      |import derevo._
      |
      |trait TwoArgsTrait[A, B]
      |
      |object kpTrait extends Derivation[TwoArgsTrait[String, *]] {
      |  def instance[T]: TwoArgsTrait[String, T] = null
      |}
      |
      |object KindProjectorTest {
      |  @derive(kpTrait) case class Foo()
      |
      |  val impl = implicitly[TwoArgsTrait[String, Foo]]
      |}
      |""".stripMargin
  )

  def testThisProjection(): Unit = checkTextHasNoErrors(
    """
      |import derevo._
      |
      |object Scope {
      |
      |  trait MyShow[A]
      |  object MyShow extends Derivation[MyShow] {
      |    def instance[A]: MyShow[A] = ???
      |  }
      |
      |  type MyShowAlias[A] = MyShow[A]
      |  object MyShowAlias extends Derivation[MyShowAlias] {
      |    def instance[A]: MyShowAlias[A] = ???
      |  }
      |
      |  @derive(MyShow)
      |  case class Foo()
      |
      |  implicitly[MyShow[Foo]]
      |
      |  @derive(MyShowAlias)
      |  case class Bar()
      |
      |  implicitly[MyShowAlias[Bar]]
      |}
      |""".stripMargin
  )
}
