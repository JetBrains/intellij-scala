package io.github.pauljamescleary.petstore.repository

import cats._
import cats.implicits._
import io.github.pauljamescleary.petstore.model.Order

import scala.collection.concurrent.TrieMap
import scala.util.Random

class OrderRepositoryInMemoryInterpreter[F[_]: Applicative] extends OrderRepositoryAlgebra[F] {

  private val cache = new TrieMap[Long, Order]

  private val random = new Random

  override def put(order: Order): F[Order] = {
    val toSave =
      if (order.id.isDefined) order
      else order.copy(id = Some(random.nextLong))

    toSave.id.foreach { cache.put(_, toSave) }
    toSave.pure[F]
  }

  override def get(orderId: Long): F[Option[Order]] =
    cache.get(orderId).pure[F]

  override def delete(orderId: Long): F[Option[Order]] =
    cache.remove(orderId).pure[F]

}

object OrderRepositoryInMemoryInterpreter {
  def apply[F[_]: Applicative]() = new OrderRepositoryInMemoryInterpreter[F]()
}
