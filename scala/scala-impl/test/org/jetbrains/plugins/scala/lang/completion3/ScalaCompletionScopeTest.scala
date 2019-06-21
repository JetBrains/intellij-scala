package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionType.BASIC
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.{PsiMember, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.junit.Assert

class ScalaCompletionScopeTest extends ScalaCodeInsightTestBase {

  private def checkCompletionsAreInScopeAndAccessible(prefix: String, completionType: CompletionType, times: Int): Unit = {
    val scope = getModule.getModuleWithDependenciesAndLibrariesScope(/*includeTests*/ true)
    configureTest(
      s"""
         |object A {
         |  $prefix$CARET
         |}""".stripMargin,
      completionType,
      times
    )

    val checkAccessibility = times <= 1

    val place = activeLookup.map(_.getPsiElement).orNull
    val allLookups = lookups(isValid)

    Assert.assertTrue(allLookups.nonEmpty)

    allLookups.foreach { lookup =>
      val psiElement = lookup.getPsiElement
      val vFile = psiElement.getContainingFile.getVirtualFile
      if (vFile != null)
        Assert.assertTrue(s"Module scope doesn't contain element from ${vFile.getCanonicalPath}", scope.contains(vFile))

      if (checkAccessibility) {
        psiElement match {
          case m: PsiMember =>
            val qName = m.qualifiedNameOpt.getOrElse(m.getName)
            Assert.assertTrue(s"$qName is not accessible", ResolveUtils.isAccessible(m, place, forCompletion = true))
        }
      }
    }
  }

  private def isValid(lookup: LookupElement): Boolean = {
    lookup.getPsiElement match {
      case _: SyntheticNamedElement => false
      case _: PsiPackage            => false
      case _: PsiNamedElement       => true
      case _                        => false
    }
  }

  def testBasicCompletion0(): Unit = checkCompletionsAreInScopeAndAccessible("File", BASIC, 0)
  def testBasicCompletion1(): Unit = checkCompletionsAreInScopeAndAccessible("File", BASIC, 1)
  def testBasicCompletion2(): Unit = checkCompletionsAreInScopeAndAccessible("File", BASIC, 2)

  def testTypeCompletion1(): Unit = checkCompletionsAreInScopeAndAccessible("null: ", BASIC, 1)
  def testTypeCompletion2(): Unit = checkCompletionsAreInScopeAndAccessible("null: ", BASIC, 2)

  def testAfterDotCompletion1(): Unit = checkCompletionsAreInScopeAndAccessible("\"\".", BASIC, 1)
  def testAfterDotCompletion2(): Unit = checkCompletionsAreInScopeAndAccessible("\"\".", BASIC, 2)

  def testBasicCompletion1EmptyPrefix(): Unit = checkCompletionsAreInScopeAndAccessible("", BASIC, 1)
}
