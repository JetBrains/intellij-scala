package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

//SCL-23032, SCL-25317
@Category(Array(classOf[TypecheckerTests]))
class KotlinObjectResolveTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_12

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.9.22"))

  def testTopLevelObjectMembers(): Unit = {
    myFixture.addFileToProject("example/TopLevel.kt",
      """package example
        |
        |object TopLevel {
        |    const val CONST: String = "const"
        |    val prop: String = "prop"
        |    fun doSomething(): Int = 42
        |    class Nested
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """import example.TopLevel
        |
        |object Usage {
        |  val c: String = TopLevel.CONST
        |  val p: String = TopLevel.getProp
        |  val r: Int = TopLevel.doSomething()
        |  val n = new TopLevel.Nested()
        |}
        |""".stripMargin
    )
  }

  def testNestedObjectMembers(): Unit = {
    myFixture.addFileToProject("example/Outer.kt",
      """package example
        |
        |object Outer {
        |    object Inner {
        |        const val CONST: String = "const"
        |        val prop: String = "prop"
        |        fun doSomething(): Int = 42
        |        class Nested
        |    }
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """import example.Outer
        |
        |object Usage {
        |  val c: String = Outer.Inner.CONST
        |  val p: String = Outer.Inner.getProp
        |  val r: Int = Outer.Inner.doSomething()
        |  val n = new Outer.Inner.Nested()
        |}
        |""".stripMargin
    )
  }

  def testCompanionObjectMembers(): Unit = {
    myFixture.addFileToProject("example/WithCompanion.kt",
      """package example
        |
        |class WithCompanion {
        |    companion object {
        |        const val CONST: String = "const"
        |        val prop: String = "prop"
        |        fun doSomething(): Int = 42
        |        @JvmStatic fun doSomethingStatic(): Int = 43
        |        class Nested
        |    }
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """import example.WithCompanion
        |
        |object Usage {
        |  val p: String = WithCompanion.Companion.getProp
        |  val r: Int = WithCompanion.Companion.doSomething()
        |  val s: Int = WithCompanion.doSomethingStatic()
        |  val n = new WithCompanion.Companion.Nested()
        |}
        |""".stripMargin
    )
  }

  def testObjectInsideClass(): Unit = {
    myFixture.addFileToProject("example/Host.kt",
      """package example
        |
        |class Host {
        |    object Named {
        |        const val CONST: String = "const"
        |        val prop: String = "prop"
        |        fun doSomething(): Int = 42
        |    }
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """import example.Host
        |
        |object Usage {
        |  val c: String = Host.Named.CONST
        |  val p: String = Host.Named.getProp
        |  val r: Int = Host.Named.doSomething()
        |}
        |""".stripMargin
    )
  }

  def testRegularClassInstanceMembersNotAccessible(): Unit = {
    myFixture.addFileToProject("example/RegularClass.kt",
      """package example
        |
        |class RegularClass {
        |    val prop: String = "prop"
        |    fun doSomething(): Int = 42
        |}
        |""".stripMargin
    )

    checkHasErrorAroundCaret(
      s"""import example.RegularClass
         |
         |object Usage {
         |  val r: Int = RegularClass.${CARET}doSomething()
         |}
         |""".stripMargin
    )
  }
}
