package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrors
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceServiceFixture
import org.jetbrains.plugins.scala.lang.typeInference.utils.SourcesCompileAndAttachLibraryFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters.ListHasAsScala

/** SCL-26415: refined result types must survive decompilation of given aliases. */
@RunWith(classOf[JUnit4])
class GivenAliasRefinementHighlightingTest extends ScalaCompilerReferenceServiceFixture {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private val librarySource = SourceFile(
    "example/Library.scala",
    """package example
      |
      |trait TC[T] {
      |  type A
      |}
      |
      |object Named {
      |  given instance: (TC[Int] { type A = Int }) = ???
      |}
      |
      |object Anonymous {
      |  given (TC[Int] { type A = Int }) = ???
      |}
      |
      |object Generic {
      |  given instance[T]: (TC[T] { type A = T }) = ???
      |}
      |
      |object AnonymousGeneric {
      |  given [T]: (TC[T] { type A = T }) = ???
      |}
      |
      |object Contextual {
      |  given instance(using String): (TC[Int] { type A = Int }) = ???
      |}
      |
      |object AnonymousContextual {
      |  given (using String): (TC[Int] { type A = Int }) = ???
      |}
      |
      |object Instance {
      |  given instance: TC[Int] with {
      |    type A = Int
      |  }
      |}
      |""".stripMargin
  )

  private val usageSource = SourceFile(
    "usage/Usage.scala",
    """package usage
      |
      |import example.*
      |
      |object NamedUsage {
      |  import Named.given
      |  val direct: TC[Int] { type A = Int } = Named.instance
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |
      |object AnonymousUsage {
      |  import Anonymous.given
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |
      |object GenericUsage {
      |  import Generic.given
      |  val direct: TC[Int] { type A = Int } = Generic.instance[Int]
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |
      |object AnonymousGenericUsage {
      |  import AnonymousGeneric.given
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |
      |object ContextualUsage {
      |  import Contextual.given
      |  given String = "context"
      |  val direct: TC[Int] { type A = Int } = Contextual.instance
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |
      |object AnonymousContextualUsage {
      |  import AnonymousContextual.given
      |  given String = "context"
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |
      |object InstanceUsage {
      |  import Instance.given
      |  val direct: TC[Int] { type A = Int } = Instance.instance
      |  val inferred: TC[Int] { type A = Int } = summon[TC[Int]]
      |}
      |""".stripMargin
  )

  @Test
  def testSource(): Unit = {
    myFixture.addFileToProject(librarySource.sourceFileName, librarySource.sourceFileContent)
    assertNoCompilationOrHighlightingErrors()
  }

  @Test
  def testCompiledLibrary(): Unit = {
    val libraryFixture = new SourcesCompileAndAttachLibraryFixture(myFixture, compiler, version)
    try {
      libraryFixture.compileSourcesAndRegisterAsLibrary(Seq(librarySource), allowWarnings = false)
      assertNoCompilationOrHighlightingErrors()
    } finally {
      libraryFixture.tearDown()
    }
  }

  private def assertNoCompilationOrHighlightingErrors(): Unit = {
    myFixture.configureByText(usageSource)
    assertNoErrors(compiler.make().asScala.toSeq)
    myFixture.checkHighlighting(false, false, false)
  }
}
