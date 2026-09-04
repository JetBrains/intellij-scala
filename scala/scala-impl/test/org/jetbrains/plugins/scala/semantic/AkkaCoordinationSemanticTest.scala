package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.scalaVersion
import org.junit.Test

class AkkaCoordinationSemanticTest extends SemanticTestBase("com.typesafe.akka" %% "akka-coordination" % "2.8.8")("akka.coordination") {
//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    akka.coordination.lease.LeaseException
    akka.coordination.lease.LeaseSettings
    akka.coordination.lease.LeaseTimeoutException
    //akka.coordination.lease.LeaseUsageSettings
    //akka.coordination.lease.TimeoutSettings
    akka.coordination.lease.internal.LeaseAdapter
    //akka.coordination.lease.internal.LeaseAdapterToScala
    akka.coordination.lease.javadsl.Lease
    //akka.coordination.lease.javadsl.LeaseProvider
    akka.coordination.lease.scaladsl.Lease
    //akka.coordination.lease.scaladsl.LeaseProvider
  """)
}