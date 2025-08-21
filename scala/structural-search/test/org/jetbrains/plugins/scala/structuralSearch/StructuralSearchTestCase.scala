package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.structuralsearch.plugin.ui.UIUtil
import com.intellij.structuralsearch.{MatchOptions, MatchResult, Matcher, StructuralSearchUtil}
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.util.SmartList

import scala.jdk.CollectionConverters
import scala.jdk.CollectionConverters.*

abstract class StructuralSearchTestCase extends LightPlatformCodeInsightTestCase {
  protected var options: MatchOptions = null

  override protected def setUp(): Unit = {
    super.setUp()
    options = new MatchOptions
    options.setRecursiveSearch(true)
  }

  override protected def tearDown(): Unit = {
    options = null
    super.tearDown()
  }

  protected def findMatches(in: String,
                            pattern: String,
                            patternFileType: LanguageFileType,
                            patternLanguage: Language,
                            sourceFileType: LanguageFileType,
                            physicalSourceFile: Boolean,
                            modifyOptions: MatchOptions => Unit
                           ): Seq[MatchResult] = {
    options.fillSearchCriteria(pattern)
    options.setFileType(patternFileType)
    options.setDialect(patternLanguage)
    modifyOptions(options)
    val compiledPattern: CompiledPattern = PatternCompiler.compilePattern(getProject, options, true, false)
    val message: String = checkApplicableConstraints(options, compiledPattern)
    assert(message == null)
    val matcher: Matcher = new Matcher(getProject, options, compiledPattern)
    matcher.testFindMatches(in, true, sourceFileType, physicalSourceFile).asScala.toSeq
  }

  def checkApplicableConstraints(options: MatchOptions, compiledPattern: CompiledPattern): String = {
    val profile = StructuralSearchUtil.getProfileByFileType(options.getFileType)
    assert(profile != null, "no profile found for file type: " + options.getFileType)
    options.getVariableConstraintNames.forEach { varName =>
      val nodes = compiledPattern.getVariableNodes(varName)
      val constraint = options.getVariableConstraint(varName)
      val usedConstraints = new SmartList[String]
      if (!StringUtil.isEmpty(constraint.getRegExp)) usedConstraints.add(UIUtil.TEXT)
      if (constraint.isWithinHierarchy) usedConstraints.add(UIUtil.TEXT_HIERARCHY)
      if (constraint.getMinCount == 0) usedConstraints.add(UIUtil.MINIMUM_ZERO)
      if (constraint.getMaxCount > 1) usedConstraints.add(UIUtil.MAXIMUM_UNLIMITED)
      if (!StringUtil.isEmpty(constraint.getNameOfExprType)) usedConstraints.add(UIUtil.TYPE)
      if (!StringUtil.isEmpty(constraint.getNameOfFormalArgType)) usedConstraints.add(UIUtil.EXPECTED_TYPE)
      if (!StringUtil.isEmpty(constraint.getReferenceConstraint)) usedConstraints.add(UIUtil.REFERENCE)
      usedConstraints.forEach { usedConstraint =>
        assert(profile.isApplicableConstraint(usedConstraint, nodes, false, constraint.isPartOfSearchResults),
          usedConstraint + " not applicable for " + varName
        )
      }
    }
    null
  }
}
