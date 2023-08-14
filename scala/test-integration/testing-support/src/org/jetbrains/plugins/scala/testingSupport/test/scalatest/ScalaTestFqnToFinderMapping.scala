package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.scalatest.finders.Finder

/**
 * This is a dirty workaround for SCL-21497<br>
 * This change is only designed for idea232.release/x branch. In branch idea233.x there will be a proper fix
 * in the decompiler (Tasty reader). After that fix finder annotations will be properly detected so this workaround is not needed.
 */
@ScheduledForRemoval
@Deprecated
private object ScalaTestFqnToFinderMapping {

  import ScalaTestUtil._

  val BaseClassToFinder: Map[String, Finder] = Map(
    featureSpecBases -> new org.scalatest.finders.FeatureSpecFinder,
    flatSpecBases -> new org.scalatest.finders.FlatSpecFinder,
    freeSpecBases -> new org.scalatest.finders.FreeSpecFinder,
    funSpecBasesPre2_0 -> new org.scalatest.finders.FunSpecFinder,
    funSpecBasesPost2_0 -> new org.scalatest.finders.FunSpecFinder,
    funSuiteBases -> new org.scalatest.finders.FunSuiteFinder,
    propSpecBases -> new org.scalatest.finders.PropSpecFinder,
    wordSpecBases -> new org.scalatest.finders.WordSpecFinder,
  ).flatMap { case bases -> finder => bases.map(_ -> finder)}
}
