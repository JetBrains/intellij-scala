package org.jetbrains.plugins.scala.testingSupport

import java.util.{ArrayList, Collections, List}
import java.util.regex.Pattern

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.util.Pair
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import com.intellij.testIntegration.{JavaTestFinder, TestFinderHelper}
import com.intellij.util.containers.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * @author Roman.Shein
 * @since 07.07.2015.
 */
class ScalaTestFinder extends JavaTestFinder {
  override def findTestsForClass(element: PsiElement): java.util.Collection[PsiElement] = {
    findSourceElement(element) match {
      case obj: ScObject =>
        val objName = obj.getName.stripSuffix("$")
        val pattern = Pattern.compile(s".*$objName.*", Pattern.CASE_INSENSITIVE)
        val names = new HashSet[String]()
        val frameworks: TestFrameworks = TestFrameworks.getInstance
        val cache = PsiShortNamesCache.getInstance(obj.getProject)
        val scope: GlobalSearchScope = getSearchScope(obj, false)
        cache.getAllClassNames(names)
        val res = new java.util.ArrayList[Pair[_ <: PsiNamedElement, Integer]]()
        import collection.JavaConversions._
        for (testClassName <- names) {
          if (pattern.matcher(testClassName).matches()) {
            for (testClass <- cache.getClassesByName(testClassName, scope)) {
              if (frameworks.isTestClass(testClass) || frameworks.isPotentialTestClass(testClass)) {
                res.add(Pair.create(testClass, TestFinderHelper.calcTestNameProximity(objName, testClassName)))
              }
            }
          }
        }
        TestFinderHelper.getSortedElements(res, true)
      case _ => //do nothing, java finder will find the classes
        Collections.emptyList()
    }
  }

  override def findClassesForTest(element: PsiElement): java.util.Collection[PsiElement] = {
    val klass: PsiClass = findSourceElement(element)
    if (klass == null) return Collections.emptySet[PsiElement]
    val scope: GlobalSearchScope = getSearchScope(element, true)
    val cache: PsiShortNamesCache = PsiShortNamesCache.getInstance(element.getProject)
    val classesWithWeights: List[Pair[_ <: PsiNamedElement, Integer]] = new ArrayList[Pair[_ <: PsiNamedElement, Integer]]
    import collection.JavaConversions._
    for (nameWithWeight <- TestFinderHelper.collectPossibleClassNamesWithWeights(klass.getName)) {
      for (aClass <- cache.getClassesByName(nameWithWeight.first, scope)) {
        if (isTestSubjectClass(aClass)) classesWithWeights.add(Pair.create(aClass, nameWithWeight.second))
      }
      for (anObject <- cache.getClassesByName(nameWithWeight.first + "$", scope)) {
        if (isTestSubjectClass(anObject)) classesWithWeights.add(Pair.create(anObject, nameWithWeight.second))
      }
    }
    TestFinderHelper.getSortedElements(classesWithWeights, false)
  }
}
