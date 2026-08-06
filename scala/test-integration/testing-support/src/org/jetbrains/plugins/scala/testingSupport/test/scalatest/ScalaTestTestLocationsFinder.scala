package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.plugins.scala.caches.{CachesUtil, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.utils.ScalaTestLocationsFinderUtils
import org.jetbrains.plugins.scala.testingSupport.test.utils.ScalaTestLocationsFinderUtils.collectTestLocationsForRefSpec
import org.scalatest.finders._


// TODO: do not show gutters on non-constants, e.g.:
//  class WordSpecClassName extends WordSpec {
//   "example" should {
//     "constant" + System.currentTimeMillis() in {
//     }
//   }
// }
object ScalaTestTestLocationsFinder {

  @RequiresReadLock
  def calculateTestLocations(definition: ScTypeDefinition): Seq[PsiElement] = cachedInUserData("calculateTestLocations", definition, CachesUtil.fileModTracker(definition.getContainingFile), Tuple1(definition)) {
    val finder = ScalaTestAstTransformer.getFinder(definition)
    finder.toSeq.flatMap(doCalculateScalaTestTestLocations(definition, _))
  }

  // NOTE 1:
  //   ScalaTestAstTransformer.getFinder is only used to determine which scalatest style is used
  //   for simplicity we now place the logic of test locations search right here
  //   when the implementation is stable we could merge this logic with org.scalatest.finders.Finder interface
  // NOTE 2: current implementation is very basic, it doesn't involve any resolve, it only analyses static names
  //   see ScalaTestConfigurationProducer.getTestClassWithTestName
  private def doCalculateScalaTestTestLocations(definition: ScTypeDefinition, finder: Finder): TestLocations =
    definition.extendsBlock.templateBody.toSeq.flatMap { body =>
      finder match {
        case _: FunSuiteFinder => funSuiteTestLocations(body)
        case _: FlatSpecFinder => flatSpecTestLocations(body)
        case _: FunSpecFinder => funSpecTestLocations(body)
        case _: WordSpecFinder => wordSpecTestLocations(body)
        case _: FreeSpecFinder => freeSpecTestLocations(body)
        case _: PropSpecFinder => propSpecTestLocations(body)
        case _: FeatureSpecFinder => featureSpecTestLocations(body)
        case _: SpecFinder => collectTestLocationsForRefSpec(body)
        case _ => Seq.empty
      }
    }

  private type TestLocations = Seq[PsiElement]

  import ScalaTestLocationsFinderUtils.collectTestLocations
  import SuiteMethodNames._

  private def funSuiteTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = false, EmptySet, FunSuiteLeaves)

  private def flatSpecTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = true, EmptySet, FlatSpecLeaves)

  private def funSpecTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = false, FunSpecNodes, FunSpecLeaves)

  private def wordSpecTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = true, WordSpecNodes, WordSpecLeaves)

  private def freeSpecTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = true, FreeSpecNodes, FreeSpecLeaves)

  private def propSpecTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = false, EmptySet, PropSpecLeaves)

  private def featureSpecTestLocations(body: ScTemplateBody): TestLocations =
    collectTestLocations(body, infixStyle = false, FeatureSpecNodes, FeatureSpecLeaves)

  private[scalatest] object SuiteMethodNames {

    val EmptySet: Set[String] = Set()

    val FunSuiteLeaves = Set("test")
    val FlatSpecLeaves = Set("in", "of")
    val PropSpecLeaves = Set("property")

    val FunSpecNodes = Set("describe")
    val FunSpecLeaves = Set("it", "they")

    val WordSpecNodes = Set("when", "should", "must", "can", "which")
    val WordSpecLeaves = Set("in")

    val FreeSpecNodes = Set("-")
    val FreeSpecLeaves = Set("in")

    val FeatureSpecNodes = Set("feature", "Feature")
    val FeatureSpecLeaves = Set("scenario", "Scenario")
  }
}
