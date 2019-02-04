package org.jetbrains.bsp.project.resolver

import ch.epfl.scala.bsp4j.{BuildTarget, BuildTargetCapabilities, BuildTargetIdentifier, BuildTargetTag}
import org.junit.Test

import scala.collection.JavaConverters._

class BspResolverLogicTest {

  /** When base dir is empty, no module is created */
  @Test def testCalculateModuleDescriptionsEmptyBaseDir(): Unit = {

    val target = new BuildTarget(
      new BuildTargetIdentifier("ePzqj://jqke:540/n/ius7/jDa/t/z78"),
      List("bla").asJava, null, List.empty.asJava,
      new BuildTargetCapabilities(true,true,true)
    )

    val descriptions = BspResolverLogic.calculateModuleDescriptions(List(target), List(), List(), List())

    assert(descriptions.isEmpty)
  }

}
