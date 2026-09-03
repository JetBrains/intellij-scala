package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.scalaVersion
import org.junit.Test

class AkkaClusterSemanticTest extends SemanticTestBase("com.typesafe.akka" %% "akka-cluster" % "2.8.8")("akka.cluster") {
//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    //akka.cluster.Cluster
    //akka.cluster.ClusterActorRefProvider
    //akka.cluster.ClusterCoreDaemon
    //akka.cluster.ClusterCoreSupervisor
    //akka.cluster.ClusterDaemon
    //akka.cluster.ClusterDeployer
    //akka.cluster.ClusterDomainEventPublisher
    //akka.cluster.ClusterEvent
    akka.cluster.ClusterGossip
    akka.cluster.ClusterHeartbeat
    //akka.cluster.ClusterHeartbeatReceiver
    //akka.cluster.ClusterHeartbeatSender
    //akka.cluster.ClusterHeartbeatSenderState
    //akka.cluster.ClusterJmx
    akka.cluster.ClusterLogClass
    //akka.cluster.ClusterLogMarker
    akka.cluster.ClusterMessage
    akka.cluster.ClusterNodeMBean
    //akka.cluster.ClusterReadView
    //akka.cluster.ClusterRemoteWatcher
    akka.cluster.ClusterScope
    //akka.cluster.ClusterSettings
    akka.cluster.ClusterUserAction
    //akka.cluster.ConfigValidation
    //akka.cluster.CoordinatedShutdownLeave
    //akka.cluster.CrossDcHeartbeatSender
    //akka.cluster.CrossDcHeartbeatingState
    //akka.cluster.DowningProvider
    //akka.cluster.FirstSeedNodeProcess
    //akka.cluster.Gossip
    //akka.cluster.GossipEnvelope
    //akka.cluster.GossipOverview
    //akka.cluster.GossipStats
    //akka.cluster.GossipStatus
    //akka.cluster.GossipTargetSelector
    //akka.cluster.HeartbeatNodeRing
    akka.cluster.InternalClusterAction
    akka.cluster.Invalid
    //akka.cluster.JoinConfigCompatCheckCluster
    //akka.cluster.JoinConfigCompatChecker
    //akka.cluster.JoinSeedNodeProcess
    //akka.cluster.Member
    //akka.cluster.MemberStatus
    //akka.cluster.MembershipState
    akka.cluster.NoDowning
    //akka.cluster.OnMemberStatusChangedListener
    //akka.cluster.Reachability
    //akka.cluster.SeedNodeProcess
    //akka.cluster.UniqueAddress
    akka.cluster.Valid
    //akka.cluster.VectorClock
    //akka.cluster.VectorClockStats
    //akka.cluster.protobuf.ClusterMessageSerializer
    //akka.cluster.routing.ClusterRouterActor
    //akka.cluster.routing.ClusterRouterConfigBase
    akka.cluster.routing.ClusterRouterGroup
    //akka.cluster.routing.ClusterRouterGroupActor
    //akka.cluster.routing.ClusterRouterGroupSettings
    //akka.cluster.routing.ClusterRouterPool
    //akka.cluster.routing.ClusterRouterPoolActor
    //akka.cluster.routing.ClusterRouterPoolSettings
    akka.cluster.routing.ClusterRouterSettingsBase
    akka.cluster.sbr.DownAllNodes
    //akka.cluster.sbr.DowningStrategy
    akka.cluster.sbr.KeepMajority
    akka.cluster.sbr.KeepOldest
    akka.cluster.sbr.KeepOldestSettings
    akka.cluster.sbr.LeaseMajority
    akka.cluster.sbr.LeaseMajoritySettings
    //akka.cluster.sbr.SplitBrainResolver
    //akka.cluster.sbr.SplitBrainResolverBase
    //akka.cluster.sbr.SplitBrainResolverProvider
    //akka.cluster.sbr.SplitBrainResolverSettings
    akka.cluster.sbr.StaticQuorum
    akka.cluster.sbr.StaticQuorumSettings
  """)
}