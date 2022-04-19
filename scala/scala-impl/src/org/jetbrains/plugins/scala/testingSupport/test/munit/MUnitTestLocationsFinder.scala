package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.testingSupport.test.utils.ScalaTestLocationsFinderUtils

private[testingSupport]
object MUnitTestLocationsFinder {

  @RequiresReadLock
  @CachedInUserData(definition, CachesUtil.fileModTracker(definition.getContainingFile))
  def calculateTestLocations(definition: ScTypeDefinition): Option[Seq[PsiElement]] = {
    import ScalaTestLocationsFinderUtils.collectTestLocations

    val templateBodyOpt = definition.extendsBlock.templateBody
    val result = templateBodyOpt.map { body =>
      val testLocations = collectTestLocations(
        body,
        infixStyle = false,
        intermediateMethodNames = Set.empty,
        leafMethodNames = MUnitUtils.FunSuiteTestMethodNames
      )
      val testLocationsWithStaticName = testLocations.filter(MUnitUtils.hasStaticTestName)
      testLocationsWithStaticName
    }
    result
  }
}
