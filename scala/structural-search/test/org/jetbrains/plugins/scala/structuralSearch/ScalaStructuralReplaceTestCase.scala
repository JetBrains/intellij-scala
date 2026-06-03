package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.structuralsearch.MatchOptions
import com.intellij.structuralsearch.impl.matcher.compiler.{PatternCompiler, StringToConstraintsTransformer}
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions
import com.intellij.structuralsearch.plugin.replace.impl.Replacer
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.lang.annotations.Language

abstract class ScalaStructuralReplaceTestCase extends LightPlatformCodeInsightTestCase {
  var options: Option[ReplaceOptions] = None

  class MatchOptionsMock extends MatchOptions {
    override def fillSearchCriteria(criteria: String): Unit =
      StringToConstraintsTransformer.transformCriteria(criteria, this)

    def resetContraints(): Unit = super.fillSearchCriteria("")
  }

  override def setUp(): Unit = {
    super.setUp()
    options = Some(new ReplaceOptions(new MatchOptionsMock))
  }

  def replaceAndAssert(name: String,
                       @Language("Scala 3") code: String,
                       @Language("Scala 3") pattern: String,
                       @Language("Scala 3") repl: String,
                       @Language("Scala 3") expected: String,
                       modifyOptions: MatchOptions => Unit = _ => ()): Unit = {
    val result = replace(code.stripMargin.strip, pattern.stripMargin.strip, repl.stripMargin.strip, false, modifyOptions)
      .split("\n").map(_.strip()).filter(_.nonEmpty).mkString("\n")
    val exp = expected.stripMargin.strip.split("\n").map(_.strip()).filter(_.nonEmpty).mkString("\n")
    assert(result == exp,
      s"Replace pattern for '$name' did not match\n$result\n  instead of\n$exp")
  }

  protected def replace(in: String, what: String, by: String, sourceIsFile: Boolean,
                        modifyOptions: MatchOptions => Unit): String = {
    val opt = options.getOrElse(throw Exception("Options not initialized"))

    val matchOptions = opt.getMatchOptions
    assert(matchOptions != null)
    matchOptions.asInstanceOf[MatchOptionsMock].resetContraints()
    matchOptions.setFileType(Scala3FileType)
    matchOptions.fillSearchCriteria(what)
    modifyOptions(matchOptions)

    val compiledPattern = PatternCompiler.compilePattern(getProject, matchOptions, true, false)
    val message = StructuralSRTestCase.checkApplicableConstraints(matchOptions, compiledPattern)
    assert(message == null)
    Replacer.testReplace(in, what, by, opt, getProject, sourceIsFile)
  }
}
