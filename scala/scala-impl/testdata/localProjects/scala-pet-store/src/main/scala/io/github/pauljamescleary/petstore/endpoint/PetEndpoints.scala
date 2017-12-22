package io.github.pauljamescleary.petstore.endpoint

import cats.data.Validated.Valid
import cats.data._
import cats.effect.Effect
import cats.implicits._
import io.circe._
import io.circe.generic.auto._
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import io.github.pauljamescleary.petstore.model.{Pet, PetStatus}
import io.github.pauljamescleary.petstore.service.PetService
import io.github.pauljamescleary.petstore.validation.{PetAlreadyExistsError, PetNotFoundError}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, HttpService, QueryParamDecoder}

import scala.language.higherKinds

class PetEndpoints[F[_]: Effect] extends Http4sDsl[F] {

  /* Necessary for decoding query parameters */
  import QueryParamDecoder._

  /* Parses out the id query param */
  object IdMatcher extends QueryParamDecoderMatcher[Long]("id")

  /* Parses out the offset and page size params */
  object PageSizeMatcher extends QueryParamDecoderMatcher[Int]("pageSize")
  object OffsetMatcher extends QueryParamDecoderMatcher[Int]("offset")

  /* Parses out status query param which could be multi param */
  implicit val statusQueryParamDecoder: QueryParamDecoder[PetStatus] =
    QueryParamDecoder[String].map(PetStatus.apply)

  /* Relies on the statusQueryParamDecoder implicit, will parse out a possible multi-value query parameter */
  object StatusMatcher extends OptionalMultiQueryParamDecoderMatcher[PetStatus]("status")

  /* Parses out tag query param, which could be multi-value */
  object TagMatcher extends OptionalMultiQueryParamDecoderMatcher[String]("tags")

  /* We need to define an enum encoder and decoder since these do not come out of the box with generic derivation */
  implicit val statusDecoder: Decoder[PetStatus] = deriveEnumerationDecoder
  implicit val statusEncoder: Encoder[PetStatus] = deriveEnumerationEncoder
  implicit val petDecoder: EntityDecoder[F, Pet] = jsonOf[F, Pet]

  private def createPetEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case req @ POST -> Root / "pets" =>
        val action = for {
          pet <- req.as[Pet]
          result <- petService.create(pet).value
        } yield result

        action.flatMap {
          case Right(saved) =>
            Ok(saved.asJson)
          case Left(PetAlreadyExistsError(existing)) =>
            Conflict(s"The pet ${existing.name} of category ${existing.category} already exists")
          case Left(unexpected) =>
            InternalServerError(s"Unexpected error: $unexpected")
        }
    }

  private def updatePetEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case req @ PUT -> Root / "pets" =>
        val action = for {
          pet <- req.as[Pet]
          result <- petService.update(pet).value
        } yield result

        action.flatMap {
          case Right(saved) => Ok(saved.asJson)
          case Left(PetNotFoundError) => NotFound("The pet was not found")
          case Left(unexpected) =>
            InternalServerError(s"Unexpected error: $unexpected")
        }
    }

  private def getPetEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "pets" :? IdMatcher(id) =>
        petService.get(id).value.flatMap {
          case Right(found) => Ok(found.asJson)
          case Left(PetNotFoundError) => NotFound("The pet was not found")
          case Left(unexpected) =>
            InternalServerError(s"Unexpected error: $unexpected")
        }
    }

  private def deletePetEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case DELETE -> Root / "pets" :? IdMatcher(id) =>
        for {
          _ <- petService.delete(id)
          resp <- Ok()
        } yield resp
    }

  private def listPetsEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "pets" :? PageSizeMatcher(pageSize) :? OffsetMatcher(offset) =>
        for {
          retrieved <- petService.list(pageSize, offset)
          resp <- Ok(retrieved.asJson)
        } yield resp
    }

  private def findPetsByStatusEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "pets" / "findByStatus" :? StatusMatcher(Valid(Nil)) =>
        // User did not specify any statuses
        BadRequest("status parameter not specified")

      case GET -> Root / "pets" / "findByStatus" :? StatusMatcher(Valid(statuses)) =>
        // We have a list of valid statuses, find them and return
        for {
          retrieved <- petService.findByStatus(NonEmptyList.fromListUnsafe(statuses))
          resp <- Ok(retrieved.asJson)
        } yield resp
    }

  private def findPetsByTagEndpoint(petService: PetService[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "pets" / "findByTags" :? TagMatcher(Valid(Nil)) =>
        BadRequest("tag parameter not specified")

      case GET -> Root / "pets" / "findByTags" :? TagMatcher(Valid(tags)) =>
        for {
          retrieved <- petService.findByTag(NonEmptyList.fromListUnsafe(tags))
          resp <- Ok(retrieved.asJson)
        } yield resp

    }

  def endpoints(petService: PetService[F]): HttpService[F] =
    createPetEndpoint(petService) <+>
      getPetEndpoint(petService) <+>
      deletePetEndpoint(petService) <+>
      listPetsEndpoint(petService) <+>
      findPetsByStatusEndpoint(petService) <+>
      updatePetEndpoint(petService) <+>
      findPetsByTagEndpoint(petService)
}

object PetEndpoints {
  def endpoints[F[_]: Effect](petService: PetService[F]): HttpService[F] =
    new PetEndpoints[F].endpoints(petService)
}
