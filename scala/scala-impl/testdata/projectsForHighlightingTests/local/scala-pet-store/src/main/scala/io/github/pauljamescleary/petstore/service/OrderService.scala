package io.github.pauljamescleary.petstore.service

import io.github.pauljamescleary.petstore.model.Order
import io.github.pauljamescleary.petstore.repository.OrderRepositoryAlgebra

import scala.language.higherKinds

class OrderService[F[_]](orderRepo: OrderRepositoryAlgebra[F]) {

  def placeOrder(order: Order): F[Order] = orderRepo.put(order)

}

object OrderService {
  def apply[F[_]](orderRepo: OrderRepositoryAlgebra[F]): OrderService[F] =
    new OrderService(orderRepo)
}
