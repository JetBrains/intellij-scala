package org.jetbrains.plugins.scala.compiler.references

final case class Timestamped[T](timestamp: Long, unwrap: T)
