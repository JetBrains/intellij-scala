package org.jetbrains.plugins.scala.kotlin.util

import scala.collection.Seq
import scala.jdk.CollectionConverters
import scala.collection.Iterator as ScalaIterator

fun <T, U> scala.util.Either<T, U>?.orNull(): U? = this?.getOrElse { null }

fun <T, S: Seq<T>> S.asJava(): List<T> = CollectionConverters.SeqHasAsJava(this).asJava()

fun <T, I: ScalaIterator<T>> I.asJava(): Iterator<T> = CollectionConverters.IteratorHasAsJava(this).asJava()
