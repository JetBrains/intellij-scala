package org.jetbrains.plugins.scala

// tests marked with these categories will be run as a separate step


trait SlowTests

trait DebuggerTests

trait ScalacTests

trait TypecheckerTests

trait TestingSupportTests

trait UltimateTests

trait WorksheetEvaluationTests

/**
 * Will only be run at night.
 *
 * Especially, they will not be run to decide whether a branch should be merged or not.
 */
trait RandomTypingTests
trait HighlightingTests

/** Tests that may fail intermittently or depending on environment. 
 * Eg run locally but not on build server. */
trait FlakyTests

/** Test cases generated from testdata file sets. Mostly contains lexer and parser tests. */
trait FileSetTests

/** Completion tests became very slow due to platform changes in the project loading and setup. */
trait CompletionTests

/**
 * Applied to [[org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase]],
 * [[org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase]],
 * [[org.jetbrains.plugins.scala.base.EditorActionTestBase]] and
 * `org.jetbrains.plugins.scala.codeInsight.InlayHintsTestBase`.
 */
trait EditorTests

trait CompilationTests

/**
 * See [[org.jetbrains.plugins.scala.internal.bundle.ScalaBundleSortingTest]]
 */
trait BundleSortingTests
