package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.psi.{PsiMember, PsiNamedElement, PsiPackage}
import org.jetbrains.plugins.scala.lang.completion.isAccessible
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticNamedElement
import org.junit.Assert.assertTrue

class ScalaCompletionScopeTest extends ScalaCodeInsightTestBase {

  import extensions._

  def testBasicCompletion0(): Unit = checkCompletionsAreInScopeAndAccessible("File", 0)

  def testBasicCompletion1(): Unit = checkCompletionsAreInScopeAndAccessible("File", 1)

  def testBasicCompletion2(): Unit = checkCompletionsAreInScopeAndAccessible("File", 2)

  def testTypeCompletion1(): Unit = checkCompletionsAreInScopeAndAccessible("null: ", 1)

  def testTypeCompletion2(): Unit = checkCompletionsAreInScopeAndAccessible("null: ", 2)

  def testAfterDotCompletion1(): Unit = checkCompletionsAreInScopeAndAccessible("\"\".", 1)

  def testAfterDotCompletion2(): Unit = checkCompletionsAreInScopeAndAccessible("\"\".", 2)

  def testBasicCompletion1EmptyPrefix(): Unit = checkCompletionsAreInScopeAndAccessible("", 1)

  private def checkCompletionsAreInScopeAndAccessible(prefix: String, invocationCount: Int): Unit = {
    val scope = getModule.getModuleWithDependenciesAndLibrariesScope(true)

    val (lookup, items) = activeLookupWithItems(
      fileText =
        s"""object A {
           |  $prefix$CARET
           |}""".stripMargin,
      invocationCount = invocationCount
    )

    val namedElements = for {
      item <- items
      itemElement = item.getPsiElement

      if (itemElement match {
        case _: SyntheticNamedElement |
             _: PsiPackage => false
        case _: PsiNamedElement => true
        case _ => false
      })
    } yield itemElement.asInstanceOf[PsiNamedElement]

    assertTrue(namedElements.nonEmpty)

    for {
      element <- namedElements
      file <- element.containingVirtualFile
    } assertTrue("Module scope doesn't contain element from " + file.getCanonicalPath, scope.contains(file))

    for {
      element <- namedElements
      if invocationCount <= 1 && element.isInstanceOf[PsiMember]

      member = element.asInstanceOf[PsiMember]
      name = member.qualifiedNameOpt.getOrElse(member.getName)
    } assertTrue(name + " is not accessible", isAccessible(member)(lookup.getPsiElement))
  }
}
