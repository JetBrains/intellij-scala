package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.given
import org.junit.Test

class CatsMtlSemanticTest extends SemanticTestBase("org.typelevel" %% "cats-mtl" % "1.3.1")("cats.mtl") {
  override protected def enableKindProjectorPlugin = true

  @Test def test(): Unit = doTest("""
    //cats.mtl.Ask
    cats.mtl.AskForMonadPartialOrder
    cats.mtl.AskInstances
    //cats.mtl.Censor
    //cats.mtl.CensorInstances
    //cats.mtl.Chronicle
    //cats.mtl.ChronicleInstances
    //cats.mtl.Handle
    //cats.mtl.HandleInstances
    cats.mtl.HandleLowPriorityInstances
    //cats.mtl.Listen
    //cats.mtl.ListenEitherT
    //cats.mtl.ListenInductiveRWST
    //cats.mtl.ListenInductiveWriterT
    //cats.mtl.ListenInstances
    //cats.mtl.ListenIorT
    //cats.mtl.ListenKleisli
    //cats.mtl.ListenOptionT
    //cats.mtl.ListenRWST
    //cats.mtl.ListenStateT
    //cats.mtl.ListenWriterT
    //cats.mtl.Local
    //cats.mtl.LocalInstances
    cats.mtl.LowPriorityAskInstances
    cats.mtl.LowPriorityAskInstancesCompat
    //cats.mtl.LowPriorityCensorInstances
    //cats.mtl.LowPriorityListenInstances
    cats.mtl.LowPriorityListenInstancesCompat
    //cats.mtl.LowPriorityLocalInstances
    cats.mtl.LowPriorityLocalInstancesCompat
    cats.mtl.LowPriorityRaiseInstances
    cats.mtl.LowPriorityStatefulInstances
    cats.mtl.LowPriorityTellInstances
    cats.mtl.LowPriorityTellInstancesCompat
    cats.mtl.MonadPartialOrder
    //cats.mtl.MonadPartialOrderInstances
    //cats.mtl.Raise
    cats.mtl.RaiseInstances
    cats.mtl.RaiseMonadPartialOrder
    //cats.mtl.Stateful
    //cats.mtl.StatefulInstances
    //cats.mtl.Tell
    //cats.mtl.TellInstances
    cats.mtl.TellMonadPartialOrder
    cats.mtl.syntax.AllSyntax
    cats.mtl.syntax.AskSyntax
    //cats.mtl.syntax.ChronicleIdOps
    //cats.mtl.syntax.ChronicleIorOps
    //cats.mtl.syntax.ChronicleOps
    cats.mtl.syntax.ChronicleSyntax
    //cats.mtl.syntax.HandleOps
    cats.mtl.syntax.HandleSyntax
    //cats.mtl.syntax.ListenOps
    cats.mtl.syntax.ListenSyntax
    //cats.mtl.syntax.LocalOps
    cats.mtl.syntax.LocalSyntax
    //cats.mtl.syntax.ModifyOps
    //cats.mtl.syntax.RaiseOps
    cats.mtl.syntax.RaiseSyntax
    //cats.mtl.syntax.ReaderOps
    //cats.mtl.syntax.SetOps
    cats.mtl.syntax.StateSyntax
    //cats.mtl.syntax.TellOps
    cats.mtl.syntax.TellSyntax
    //cats.mtl.syntax.TupleOps
    cats.mtl.syntax.all
    cats.mtl.syntax.ask
    cats.mtl.syntax.chronicle
    cats.mtl.syntax.handle
    cats.mtl.syntax.listen
    cats.mtl.syntax.local
    cats.mtl.syntax.raise
    cats.mtl.syntax.state
    cats.mtl.syntax.tell
  """)
}