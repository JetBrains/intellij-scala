package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.plugins.scala.caches.{CachesUtil, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.utils.ScalaTestLocationsFinderUtils

private[testingSupport]
object MUnitTestLocationsFinder {

  @RequiresReadLock
  def calculateTestLocations(definition: ScTypeDefinition): Option[Seq[PsiElement]] = _calculateTestLocations(definition)

  private val _calculateTestLocations = (holder: ScTypeDefinition) => cachedInUserData("MUnitTestLocationsFinder.calculateTestLocations", holder, CachesUtil.fileModTracker(holder.getContainingFile), (definition: ScTypeDefinition) => {
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
  }).apply(holder)
}
