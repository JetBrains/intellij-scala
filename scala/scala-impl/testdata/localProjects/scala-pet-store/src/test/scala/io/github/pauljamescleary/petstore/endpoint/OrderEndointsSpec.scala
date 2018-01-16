package io.github.pauljamescleary.petstore
package endpoint

import cats.effect._
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.extras.semiauto._
import io.circe.java8.time._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import model._
import repository._
import service._

class OrderEndpointsSpec
    extends FunSuite
    with Matchers
    with PropertyChecks
    with PetStoreArbitraries
    with Http4sDsl[IO] {

  implicit val statusDecoder: Decoder[OrderStatus] = deriveEnumerationDecoder
  implicit val statusEncoder: Encoder[OrderStatus] = deriveEnumerationEncoder

  test("place order") {

    val orderService = OrderService(OrderRepositoryInMemoryInterpreter[IO])
    val orderHttpService = OrderEndpoints.endpoints[IO](orderService)

    forAll { (order: Order) =>
      val placeOrderReq =
        Request[IO](Method.POST, Uri.uri("/orders")).withBody(order.asJson)

      (for {
        request <- placeOrderReq
        response <- orderHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
      } yield {
        response.status shouldEqual Ok
      }).unsafeRunSync
    }

  }

}
