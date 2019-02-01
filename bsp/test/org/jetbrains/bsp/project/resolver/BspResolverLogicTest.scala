package org.jetbrains.bsp.project.resolver

import java.nio.file.Path

import ch.epfl.scala.bsp.gen.Bsp4jArbitrary._
import ch.epfl.scala.bsp.gen.UtilGenerators._
import ch.epfl.scala.bsp4j._
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bsp.project.resolver.BspResolverLogic._
import org.scalacheck.Prop.{BooleanOperators, forAll}
import org.scalacheck._

object BspResolverLogicTest extends Properties("BspResolverLogic functions") {

  property("commonBase") = forAll(Gen.listOf(genPath)) { paths: List[Path] =>
    val files = paths.map(_.toFile)
    val base = commonBase(files)
    val findsBase =
      files.nonEmpty ==> base.isDefined
    val baseIsAncestor =
      (files.size > 1) ==> files.forall { f => FileUtil.isAncestor(base.get, f, false) }

    findsBase && baseIsAncestor
  }

  property("getScalaSdkData succeeds") = forAll { (scalaBuildTarget: ScalaBuildTarget, scalacOptionsItem: ScalacOptionsItem) =>

    val data = getScalaSdkData(scalaBuildTarget, Some(scalacOptionsItem))
    val jarsToClasspath = ! scalaBuildTarget.getJars.isEmpty ==> ! data.scalacClasspath.isEmpty

    jarsToClasspath && data.scalaVersion.isDefined
  }

}
