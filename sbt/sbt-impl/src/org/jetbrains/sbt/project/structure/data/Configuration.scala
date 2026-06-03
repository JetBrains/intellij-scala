package org.jetbrains.sbt.project.structure.data

case class Configuration(name: String):
  override def toString: String = name

object Configuration:
  val Compile: Configuration  = Configuration("compile")
  val Test: Configuration     = Configuration("test")
  val Runtime: Configuration  = Configuration("runtime")
  val Provided: Configuration = Configuration("provided")

  def fromString(confStr: String): Seq[Configuration] =
    if (confStr.isEmpty) Seq.empty
    else
      val parts = confStr.split(";")
      val builder = scala.collection.immutable.Seq.newBuilder[String]
      builder ++= parts
      builder.result().map(Configuration(_))
