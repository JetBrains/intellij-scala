package org.jetbrains.plugins.scala.corpus

import org.jetbrains.plugins.scala.corpus.scala2.Scala2ProjectCorpusTestDef
import org.jetbrains.plugins.scala.corpus.scala3.Scala3ProjectCorpusTestDef

object CorpusProjects {
  case class OnlyScala2Project(scala2: Scala2ProjectCorpusTestDef)
  case class OnlyScala3Project(scala3: Scala3ProjectCorpusTestDef)
  case class Scala2Scala3Project(scala2: Scala2ProjectCorpusTestDef, scala3: Scala3ProjectCorpusTestDef)

  val Akka = Scala2Scala3Project(scala2.AkkaTest, scala3.AkkaTest)
  val Ammonite = Scala2Scala3Project(scala2.AmmoniteTest, scala3.AmmoniteTest)
  val Cats = Scala2Scala3Project(scala2.CatsTest, scala3.CatsTest)
  val Circe = Scala2Scala3Project(scala2.CirceTest, scala3.CirceTest)
  val Doobie = Scala2Scala3Project(scala2.DoobieTest, scala3.DoobieTest)
  val Fs2 = Scala2Scala3Project(scala2.Fs2Test, scala3.Fs2Test)
  val Jsoniter = Scala2Scala3Project(scala2.JsoniterTest, scala3.JsoniterTest)
  val Mill = Scala2Scala3Project(scala2.MillTest, scala3.MillTest)
  val Play = Scala2Scala3Project(scala2.PlayTest, scala3.PlayTest)
  val Quill = Scala2Scala3Project(scala2.QuillTest, scala3.QuillTest)
  val Scalacheck = Scala2Scala3Project(scala2.ScalacheckTest, scala3.ScalacheckTest)
  val ScalaCompiler = Scala2Scala3Project(scala2.ScalaCompilerTest, scala3.ScalaCompilerTest)
  val Scalactic = Scala2Scala3Project(scala2.ScalacticTest, scala3.ScalacticTest)
  val ScalaJavaTime = Scala2Scala3Project(scala2.ScalaJavaTimeTest, scala3.ScalaJavaTimeTest)
  val ScalaLibrary = Scala2Scala3Project(scala2.ScalaLibraryTest, scala3.ScalaLibraryTest)
  val Scalatest = Scala2Scala3Project(scala2.ScalatestTest, scala3.ScalatestTest)
  val Scalaz = Scala2Scala3Project(scala2.ScalazTest, scala3.ScalazTest)
  val Zio = Scala2Scala3Project(scala2.ZioTest, scala3.ZioTest)

  val ScalaReflect = OnlyScala2Project(scala2.ScalaReflectTest)
  val ScalaLibrary_3_8 = OnlyScala3Project(scala3.ScalaLibrary_3_8_Test)

  val all: Seq[ProjectCorpusTestDef] =
    Seq(
      // Projects with both Scala 2 and Scala 3
      Akka,
      Ammonite,
      Cats,
      Circe,
      Doobie,
      Fs2,
      Jsoniter,
      Mill,
      Play,
      Quill,
      Scalacheck,
      ScalaCompiler,
      Scalactic,
      ScalaJavaTime,
      ScalaLibrary,
      Scalatest,
      Scalaz,
      Zio,
    ).flatMap(both => Seq(both.scala2, both.scala3)) ++
      Seq(
        // Projects with only Scala 2 or Scala 3
        ScalaReflect.scala2,
        ScalaLibrary_3_8.scala3,
      )
}
