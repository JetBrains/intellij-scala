package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.given
import org.junit.Test

class CatsFreeSemanticTest extends SemanticTestBase("org.typelevel" %% "cats-free" % "2.13.0")("cats.free") {
  override protected def enableKindProjectorPlugin = true

//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    //cats.free.Cofree
    //cats.free.CofreeComonad
    cats.free.CofreeInstances
    cats.free.CofreeInstances1
    cats.free.CofreeInstances2
    //cats.free.CofreeReducible
    cats.free.CofreeTraverse
    //cats.free.ContravariantCoyoneda
    //cats.free.Coyoneda
    //cats.free.Free
    //cats.free.FreeApplicative
    //cats.free.FreeFoldStep
    cats.free.FreeFoldable
    //cats.free.FreeInstances
    cats.free.FreeInstances1
    //cats.free.FreeInvariantMonoidal
    cats.free.FreeStructuralInstances
    //cats.free.FreeStructuralInstances0
    //cats.free.FreeStructuralInstances1
    //cats.free.FreeStructuralInstances2
    //cats.free.FreeT
    cats.free.FreeTFlatMap
    //cats.free.FreeTInstances
    cats.free.FreeTInstances0
    cats.free.FreeTInstances1
    //cats.free.FreeTInstances2
    //cats.free.FreeTInstances3
    cats.free.FreeTMonad
    cats.free.FreeTMonoidK
    //cats.free.FreeTSemigroupK
    //cats.free.FreeTraverse
    //cats.free.InvariantCoyoneda
    cats.free.TrampolineFunctions
    //cats.free.Yoneda
  """)
}