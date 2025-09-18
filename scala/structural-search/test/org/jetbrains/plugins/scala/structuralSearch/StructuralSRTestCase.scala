package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.openapi.util.text.StringUtil
import com.intellij.structuralsearch.impl.matcher.CompiledPattern
import com.intellij.structuralsearch.plugin.ui.UIUtil
import com.intellij.structuralsearch.{MatchOptions, StructuralSearchUtil}
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.util.SmartList

abstract class StructuralSRTestCase extends LightPlatformCodeInsightTestCase {
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
}

object StructuralSRTestCase {
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
