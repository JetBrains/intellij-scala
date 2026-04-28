package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrors
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceServiceFixture
import org.jetbrains.plugins.scala.lang.typeInference.utils.SourcesCompileAndAttachLibraryFixture

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * See SCL-25353
 *
 * This test is located in the compiler-integration module because it uses the Scala compiler (via `SourcesCompileAndAttachLibraryFixture`).<br>
 * The reference service is used to make that the test code depends on the decompiled code from a library
 */
class DerivesUsageHighlightingTest_WithCompiledLibraryDependency extends ScalaCompilerReferenceServiceFixture {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private var compiledLibraryFixture: SourcesCompileAndAttachLibraryFixture = _

  override def setUp(): Unit = {
    super.setUp()

    compiledLibraryFixture = new SourcesCompileAndAttachLibraryFixture(myFixture, compiler, version)
  }

  override def tearDown(): Unit = {
    try {
      if (compiledLibraryFixture != null) {
        compiledLibraryFixture.tearDown()
      }
    } finally {
      compiledLibraryFixture = null
      super.tearDown()
    }
  }

  private val usageSourceFile = SourceFile(
    sourceFileName = "example/app/Usage.scala",
    sourceFileContent =
      """package example.app
        |
        |import cats.syntax.all.*
        |import example.model.Bar
        |
        |object Usage1 {
        |  Bar(42).map(_ + 1)
        |}
        |
        |object Usage2 {
        |  import example.model.Bar.derived$Functor
        |}
        |
        |object Usage3 {
        |  summon[cats.Functor[Bar]]
        |}
        |""".stripMargin
  )

  private val barSourceFile = SourceFile(
    sourceFileName = "example/model/Bar.scala",
    sourceFileContent =
      """package example.model
        |
        |import cats.Functor
        |import cats.derived.*
        |
        |case class Bar[A](value: A) derives Functor
        |""".stripMargin
  )

  private val barSourceFile_WithAliasedTypeClass = SourceFile(
    sourceFileName = "example/model/Bar.scala",
    sourceFileContent =
      """package example.model
        |
        |import cats.Functor as MyFunctor
        |import cats.derived.*
        |
        |case class Bar[A](value: A) derives MyFunctor
        |""".stripMargin
  )

  private val barSourceFile_WithFullyQualifiedTypeClass = SourceFile(
    sourceFileName = "example/model/Bar.scala",
    sourceFileContent =
      """package example.model
        |
        |import cats.derived.*
        |
        |case class Bar[A](value: A) derives _root_.cats.Functor
        |""".stripMargin
  )

  // Minimized code from the "cats" library with these library coordinates:
  // "org.typelevel" %% "cats-core" % "2.13.0",
  // "org.typelevel" %% "kittens" % "3.5.0", //needed for `import cats.derived.*`
  private val catsSupportSourceFile = SourceFile(
    sourceFileName = "cats/CatsSupport.scala",
    sourceFileContent =
      """package cats
        |
        |trait Functor[F[_]] {
        |  extension [A](fa: F[A]) def map[B](f: A => B): F[B]
        |}
        |
        |object Functor {
        |  inline def derived[F[_]]: Functor[F] =
        |    new Functor[F] {
        |      extension [A](fa: F[A]) def map[B](f: A => B): F[B] =
        |        fa.asInstanceOf[F[B]]
        |    }
        |}
        |
        |object derived {
        |  export Functor.derived
        |}
        |
        |package syntax {
        |  object all {
        |    extension [F[_], A](fa: F[A])(using functor: cats.Functor[F])
        |      def map[B](f: A => B): F[B] = functor.map(fa)(f)
        |  }
        |}
        |""".stripMargin
  )

  def testHighlighting_WithLibraryCode_InSameSources(): Unit = {
    withCodeInSameSources(Seq(barSourceFile, catsSupportSourceFile)) {
      assertNoCompilationOrHighlightingErrors(usageSourceFile)
    }
  }

  def testHighlighting_WithLibraryCode_InCompiledBinaries(): Unit = {
    withCompiledLibrary(Seq(barSourceFile, catsSupportSourceFile), allowWarnings = true) {
      assertNoCompilationOrHighlightingErrors(usageSourceFile)
    }
  }

  def testHighlighting_WithAliasedTypeclass_InSameSources(): Unit = {
    withCodeInSameSources(Seq(barSourceFile_WithAliasedTypeClass, catsSupportSourceFile)) {
      assertNoCompilationOrHighlightingErrors(usageSourceFile)
    }
  }

  def testHighlighting_WithAliasedTypeclass_InCompiledBinaries(): Unit = {
    withCompiledLibrary(Seq(barSourceFile_WithAliasedTypeClass, catsSupportSourceFile), allowWarnings = true) {
      assertNoCompilationOrHighlightingErrors(usageSourceFile)
    }
  }

  def testHighlighting_WithFullyQualifiedTypeclass_InSameSources(): Unit = {
    withCodeInSameSources(Seq(barSourceFile_WithFullyQualifiedTypeClass, catsSupportSourceFile)) {
      assertNoCompilationOrHighlightingErrors(usageSourceFile)
    }
  }

  def testHighlighting_WithFullyQualifiedTypeclass_InCompiledBinaries(): Unit = {
    withCompiledLibrary(Seq(barSourceFile_WithFullyQualifiedTypeClass, catsSupportSourceFile), allowWarnings = true) {
      assertNoCompilationOrHighlightingErrors(usageSourceFile)
    }
  }

  private def withCodeInSameSources(librarySourceFiles: Seq[SourceFile])(assertions: => Unit): Unit = {
    myFixture.addFilesToProject(librarySourceFiles)
    assertions
  }

  private def withCompiledLibrary(
    librarySourceFiles: Seq[SourceFile],
    allowWarnings: Boolean
  )(assertions: => Unit): Unit = {
    // This minimized copy of Cats/Kittens support code and the aliased typeclass example
    // emit known Scala 3 warnings during compilation; warnings are expected in this scenario.
    compiledLibraryFixture.compileSourcesAndRegisterAsLibrary(
      librarySourceFiles,
      allowWarnings = allowWarnings
    )
    assertions
  }

  /**
   * We run an extra assertion for "No compilation errors" primarily to ensure that the test data is correct.
   * And to check that we are right to expect no highlighting errors.
   *
   * Note, however, that this makes the test relatively-heavyweight compared to regular type inference or highlighting tests
   */
  private def assertNoCompilationOrHighlightingErrors(usageSource: SourceFile): Unit = {
    myFixture.configureByText(usageSource)

    val compilerMessages = compiler.make().asScala.toSeq
    assertNoErrors(compilerMessages)

    myFixture.checkHighlighting(false, false, false)
  }
}
