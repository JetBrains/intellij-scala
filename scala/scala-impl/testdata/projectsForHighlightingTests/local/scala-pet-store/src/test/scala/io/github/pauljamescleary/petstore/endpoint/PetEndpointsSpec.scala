package io.github.pauljamescleary.petstore
package endpoint

import cats.effect._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.generic.extras.semiauto._
import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import model._
import repository._
import service._
import validation._

class PetEndpointsSpec
    extends FunSuite
    with Matchers
    with PropertyChecks
    with PetStoreArbitraries
    with Http4sDsl[IO] {

  implicit val statusDecoder: Decoder[PetStatus] = deriveEnumerationDecoder
  implicit val statusEncoder: Encoder[PetStatus] = deriveEnumerationEncoder

  test("create pet") {

    val petRepo = PetRepositoryInMemoryInterpreter[IO]()
    val petValidation = PetValidationInterpreter[IO](petRepo)
    val petService = PetService[IO](petRepo, petValidation)
    val petHttpService = PetEndpoints.endpoints[IO](petService)

    forAll { (pet: Pet) =>
      (for {
        request <- Request[IO](Method.POST, Uri.uri("/pets"))
          .withBody(pet.asJson)
        response <- petHttpService
          .run(request)
          .getOrElse(fail(s"Request was not handled: $request"))
      } yield {
        response.status shouldEqual Ok
      }).unsafeRunSync
    }

  }

  test("update pet") {

    val petRepo = PetRepositoryInMemoryInterpreter[IO]()
    val petValidation = PetValidationInterpreter[IO](petRepo)
    val petService = PetService[IO](petRepo, petValidation)
    val petHttpService = PetEndpoints.endpoints[IO](petService)

    implicit val petDecoder = jsonOf[IO, Pet]

    forAll { (pet: Pet) =>
      (for {
        createRequest <- Request[IO](Method.POST, Uri.uri("/pets"))
          .withBody(pet.asJson)
        createResponse <- petHttpService
          .run(createRequest)
          .getOrElse(fail(s"Request was not handled: $createRequest"))
        createdPet <- createResponse.as[Pet]
        petToUpdate = createdPet.copy(name = createdPet.name.reverse)
        updateRequest <- Request[IO](Method.PUT, Uri.uri("/pets"))
          .withBody(petToUpdate.asJson)
        updateResponse <- petHttpService
          .run(updateRequest)
          .getOrElse(fail(s"Request was not handled: $updateRequest"))
        updatedPet <- updateResponse.as[Pet]
      } yield {
        updatedPet.name shouldEqual pet.name.reverse
      }).unsafeRunSync
    }

  }

}
