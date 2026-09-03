package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.scalaVersion
import org.junit.Test

class ScalacheckSemanticTest extends SemanticTestBase("org.scalacheck" %% "scalacheck" % "1.19.0")("org.scalacheck") {
//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    //org.scalacheck.Arbitrary
    //org.scalacheck.ArbitraryArities
    //org.scalacheck.ArbitraryLowPriority
    //org.scalacheck.Cogen
    //org.scalacheck.CogenArities
    //org.scalacheck.CogenLowPriority
    org.scalacheck.CogenVersionSpecific
    //org.scalacheck.Gen
    //org.scalacheck.GenArities
    org.scalacheck.GenSpecificationVersionSpecific
    //org.scalacheck.GenVersionSpecific
    //org.scalacheck.Platform
    //org.scalacheck.Prop
    //org.scalacheck.PropFromFun
    //org.scalacheck.Properties
    //org.scalacheck.ScalaCheckFramework
    //org.scalacheck.ScalaCheckRunner
    org.scalacheck.ScalaVersionSpecific
    //org.scalacheck.Shrink
    //org.scalacheck.ShrinkFractional
    //org.scalacheck.ShrinkIntegral
    //org.scalacheck.ShrinkLowPriority
    org.scalacheck.ShrinkVersionSpecific
    //org.scalacheck.Test
    //org.scalacheck.commands.Commands
    //org.scalacheck.rng.Seed
    //org.scalacheck.time.JavaTimeArbitrary
    //org.scalacheck.time.JavaTimeChoose
    //org.scalacheck.time.JavaTimeCogen
    //org.scalacheck.time.JavaTimeShrink
    //org.scalacheck.util.ArrayListBuilder
    //org.scalacheck.util.Buildable
    org.scalacheck.util.BuildableVersionSpecific
    //org.scalacheck.util.CmdLineParser
    //org.scalacheck.util.ConsoleReporter
    //org.scalacheck.util.FreqMap
    //org.scalacheck.util.HashMapBuilder
    //org.scalacheck.util.Pretty
    //org.scalacheck.util.SerializableCanBuildFroms
  """)
}