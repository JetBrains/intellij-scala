package org.jetbrains.plugins.scala.kotlin.util

import scala.collection.Seq
import scala.jdk.CollectionConverters
import scala.collection.Iterator as ScalaIterator

/**
 * `Option.orNull` is defined as `def orNull[A1 >: A](implicit ev: Null <:< A1): A1`
 * and cannot be called from Java/Kotlin because of the `ev`
 */
fun <T> scala.Option<T>?.orNull(): T? = this?.getOrElse { null }

fun <T, U> scala.util.Either<T, U>?.orNull(): U? = this?.getOrElse { null }

fun <T, S: Seq<T>> S.asJava(): List<T> = CollectionConverters.SeqHasAsJava(this).asJava()

fun <T, I: ScalaIterator<T>> I.asJava(): Iterator<T> = CollectionConverters.IteratorHasAsJava(this).asJava()
