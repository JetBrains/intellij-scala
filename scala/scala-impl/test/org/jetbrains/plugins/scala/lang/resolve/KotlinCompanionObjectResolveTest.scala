package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

//SCL-23032
@Category(Array(classOf[TypecheckerTests]))
class KotlinCompanionObjectResolveTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_12

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.9.22"))

  def testKotlinCompanionObjectMethodViaCompanion(): Unit = {
    myFixture.addFileToProject("example/Foo.kt",
      """package example
        |
        |class Foo private constructor(private val i: Int) {
        |    companion object {
        |        fun bar(i: Int) = Foo(i)
        |        @JvmStatic fun baz(i: Int) = Foo(i)
        |    }
        |
        |    override fun toString(): String = "Foo($i)"
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """import example.Foo
        |
        |object FooUsageFromScala {
        |  val foo1 = Foo.Companion.bar(1)
        |  val foo2 = Foo.baz(2)
        |  val foo3 = Foo.Companion.baz(3)
        |}
        |""".stripMargin
    )
  }
}
