package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.DependencyManagerBase.{Resolver, UseJetBrainsMavenCentralMirrorPropertyKey}
import org.jetbrains.plugins.scala.util.RevertableChange.withModifiedSystemProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DependencyManagerBaseTest {

  // `resolvers` is protected, so expose it through a subclass
  private class TestDependencyManager extends DependencyManagerBase {
    def currentResolvers: Seq[Resolver] = resolvers
  }

  @Test
  def testMavenCentralIsUsedWhenThePropertyIsDisabled(): Unit =
    withModifiedSystemProperty(UseJetBrainsMavenCentralMirrorPropertyKey, "false") {
      assertEquals(Seq(Resolver.MavenCentral), new TestDependencyManager().currentResolvers)
    }

  @Test
  def testMavenCentralIsUsedWhenThePropertyHasAnUnexpectedValue(): Unit =
    withModifiedSystemProperty(UseJetBrainsMavenCentralMirrorPropertyKey, "not-a-boolean") {
      assertEquals(Seq(Resolver.MavenCentral), new TestDependencyManager().currentResolvers)
    }

  @Test
  def testMavenCentralIsUsedWhenThePropertyIsEmpty(): Unit =
    withModifiedSystemProperty(UseJetBrainsMavenCentralMirrorPropertyKey, "") {
      assertEquals(Seq(Resolver.MavenCentral), new TestDependencyManager().currentResolvers)
    }

  //SCL-25601
  @Test
  def testJetBrainsMavenCentralMirrorIsUsedWhenThePropertyIsEnabled(): Unit =
    withModifiedSystemProperty(UseJetBrainsMavenCentralMirrorPropertyKey, "true") {
      assertEquals(Seq(Resolver.JetBrainsMavenCentralMirror), new TestDependencyManager().currentResolvers)
    }
}
