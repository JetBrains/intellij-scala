package org.jetbrains.plugins.scala.semantic

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.semantic.SemanticTestBase.scalaVersion
import org.junit.Test

class ScalazEffectSemanticTest extends SemanticTestBase("org.scalaz" %% "scalaz-effect" % "7.3.8")("scalaz.effect", "scalaz.std.effect", "scalaz.syntax.effect") {
  override protected def enableKindProjectorPlugin = true

//  @Test def single(): Unit = doTest("")

  @Test def test(): Unit = doTest("""
    //scalaz.effect.Dup
    //scalaz.effect.DupInstances
    scalaz.effect.Effect
    scalaz.effect.Effects
    scalaz.effect.FinalizerHandle
    //scalaz.effect.IO
    //scalaz.effect.IOInstances
    //scalaz.effect.IOInstances0
    //scalaz.effect.IOInstances1
    scalaz.effect.IOLiftIO
    //scalaz.effect.IOMonad
    scalaz.effect.IOMonadCatchIO
    //scalaz.effect.IORef
    scalaz.effect.IORefs
    //scalaz.effect.IoExceptionOr
    //scalaz.effect.IsomorphismLiftControlIO
    scalaz.effect.IsomorphismLiftIO
    scalaz.effect.IsomorphismMonadCatchIO
    scalaz.effect.IsomorphismMonadControlIO
    scalaz.effect.IsomorphismMonadIO
    scalaz.effect.IsomorphismResource
    scalaz.effect.IvoryTower
    scalaz.effect.IvoryTowers
    //scalaz.effect.LiftControlIO
    //scalaz.effect.LiftIO
    //scalaz.effect.MonadCatchIO
    //scalaz.effect.MonadControlIO
    //scalaz.effect.MonadIO
    //scalaz.effect.RefCountedFinalizer
    scalaz.effect.RefCountedFinalizers
    //scalaz.effect.RegionT
    scalaz.effect.RegionTInstances
    //scalaz.effect.RegionTInstances1
    //scalaz.effect.RegionTLiftIO
    scalaz.effect.RegionTMonad
    //scalaz.effect.Resource
    //scalaz.effect.ST
    //scalaz.effect.STArray
    scalaz.effect.STInstance0
    //scalaz.effect.STInstances
    //scalaz.effect.STRef
    scalaz.effect.STRefInstances
    scalaz.effect.SafeApp
    scalaz.effect.Tower
    scalaz.std.effect.AllEffectInstances
    scalaz.std.effect.AutoCloseableInstances
    scalaz.std.effect.AutoCloseableInstances0
    scalaz.std.effect.FutureFunctions
    scalaz.std.effect.autoCloseable
    scalaz.std.effect.scalaFuture
    scalaz.syntax.effect.EffectSyntax
    scalaz.syntax.effect.EffectSyntaxes
    //scalaz.syntax.effect.IdOps
    //scalaz.syntax.effect.LiftControlIOOps
    //scalaz.syntax.effect.LiftControlIOSyntax
    //scalaz.syntax.effect.LiftIOOps
    //scalaz.syntax.effect.LiftIOSyntax
    scalaz.syntax.effect.MonadCatchIOOps
    scalaz.syntax.effect.MonadCatchIOSyntax
    //scalaz.syntax.effect.MonadControlIOOps
    //scalaz.syntax.effect.MonadControlIOSyntax
    //scalaz.syntax.effect.MonadIOOps
    //scalaz.syntax.effect.MonadIOSyntax
    //scalaz.syntax.effect.ResourceOps
    //scalaz.syntax.effect.ResourceSyntax
    scalaz.syntax.effect.ToAllEffectTypeClassOps
    scalaz.syntax.effect.ToIdOps
    //scalaz.syntax.effect.ToLiftControlIOOps
    //scalaz.syntax.effect.ToLiftControlIOOps0
    //scalaz.syntax.effect.ToLiftControlIOOpsU
    //scalaz.syntax.effect.ToLiftIOOps
    //scalaz.syntax.effect.ToLiftIOOps0
    //scalaz.syntax.effect.ToLiftIOOpsU
    scalaz.syntax.effect.ToMonadCatchIOOps
    scalaz.syntax.effect.ToMonadCatchIOOps0
    //scalaz.syntax.effect.ToMonadControlIOOps
    //scalaz.syntax.effect.ToMonadControlIOOps0
    //scalaz.syntax.effect.ToMonadControlIOOpsU
    //scalaz.syntax.effect.ToMonadIOOps
    //scalaz.syntax.effect.ToMonadIOOps0
    //scalaz.syntax.effect.ToMonadIOOpsU
    scalaz.syntax.effect.ToResourceOps
  """)
}