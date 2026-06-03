package org.jetbrains.plugins.scala.project

import junit.framework.TestCase
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

import scala.collection.mutable

class Source3OptionsTest extends TestCase {
  import Source3Options.{none => noOpts, all => allOpts}

  val allFeaturesOpts = allOpts.copy(isSource3Enabled = false)

  private def doTest(compilerArgs: String, expected: Source3Options): Unit = {
    val actual = Source3Options.fromAdditionalCompilerFlags(compilerArgs.split(" ").to(mutable.LinkedHashSet))
    actual shouldBe expected
  }

  def test_all_has_every_name(): Unit = {
    allOpts.featureNames shouldBe Source3Options.Names.all
  }

  def test_none_has_no_name(): Unit = {
    noOpts.featureNames shouldBe Set.empty
  }

  def test_only_XSource3(): Unit = doTest(
    "-Xsource:3",
    noOpts.copy(isSource3Enabled = true)
  )

  def test_only_XSource3_cross(): Unit = doTest(
    "-Xsource:3-cross",
    allOpts
  )

  def test_XSource3_and_XSource3_cross(): Unit = doTest(
    "-Xsource:3 -Xsource:3-cross",
    allOpts
  )

  def test_source_features(): Unit = doTest(
    "-Xsource-features:any2stringadd,unicode-escapes-raw",
    noOpts.copy(
      any2StringAdd = true,
      unicodeEscapesRaw = true
    )
  )

  def test_source_features_twice(): Unit = doTest(
    "-Xsource-features:any2stringadd,any2stringadd",
    noOpts.copy(
      any2StringAdd = true,
    )
  )

  def test_only_neg_feature(): Unit = doTest(
    "-Xsource-features:-any2stringadd",
    noOpts
  )

  def test_dont_remove_explicit_feature(): Unit = doTest(
    "-Xsource-features:any2stringadd,-any2stringadd",
    noOpts.copy(
      any2StringAdd = true
    )
  )

  def test_removing_from_all(): Unit = doTest(
    "-Xsource-features:_,-unicode-escapes-raw",
    allFeaturesOpts.copy(
      unicodeEscapesRaw = false,
    )
  )

  def test_removing_from_all2(): Unit = doTest(
    "-Xsource-features:-unicode-escapes-raw,_",
    allFeaturesOpts.copy(
      unicodeEscapesRaw = false,
    )
  )

  def test_args_are_added(): Unit = doTest(
    "-Xsource-features:any2stringadd -Xsource-features:unicode-escapes-raw",
    noOpts.copy(
      any2StringAdd = true,
      unicodeEscapesRaw = true,
    )
  )

  def test_args_dont_substract(): Unit = doTest(
    "-Xsource-features:any2stringadd -Xsource-features:-any2stringadd",
    noOpts.copy(
      any2StringAdd = true,
    )
  )

  def test_unknown_feature(): Unit = doTest(
    "-Xsource-features:unknown",
    noOpts
  )
}
