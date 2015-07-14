package org.jetbrains.plugins.scala.testingSupport

import java.util.Collections
import java.util.regex.Pattern

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.util.Pair
import com.intellij.psi.{PsiNamedElement, PsiElement, PsiClass}
import com.intellij.psi.search.{PsiShortNamesCache, GlobalSearchScope}
import com.intellij.testIntegration.{TestFinderHelper, JavaTestFinder}
import com.intellij.util.containers.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * @author Roman.Shein
 * @since 07.07.2015.
 */
class ScalaTestFinder extends JavaTestFinder {
  override def findTestsForClass(element: PsiElement): java.util.Collection[PsiElement] = {
    val klass: PsiClass = findSourceElement(element)
    if (klass == null) return Collections.emptySet()
    val klassName = klass.getName.replace("$", "\\$")
    val pattern = Pattern.compile(".*" + klassName + ".*", Pattern.CASE_INSENSITIVE)
    val names = new HashSet[String]()
    val frameworks: TestFrameworks = TestFrameworks.getInstance
    val cache = PsiShortNamesCache.getInstance(klass.getProject)
    val scope: GlobalSearchScope = getSearchScope(klass, false)
    cache.getAllClassNames(names)
    val res = new java.util.ArrayList[Pair[_ <: PsiNamedElement, Integer]]()
    import collection.JavaConversions._
    for (testClassName <- names) {
      if (pattern.matcher(testClassName).matches()) {
        for (testClass <- cache.getClassesByName(testClassName, scope)) {
          if (frameworks.isTestClass(testClass) || frameworks.isPotentialTestClass(testClass)) {
            res.add(Pair.create(testClass, TestFinderHelper.calcTestNameProximity(klassName, testClassName)))
          }
        }
      }
    }

    TestFinderHelper.getSortedElements(res, true)
  }

  override def findClassesForTest(element: PsiElement) = {
    //this is a temporary hack to avoid further duplication - JavaTestFinder locates tests for scala just fine
    new java.util.ArrayList[PsiElement]()
  }
}
