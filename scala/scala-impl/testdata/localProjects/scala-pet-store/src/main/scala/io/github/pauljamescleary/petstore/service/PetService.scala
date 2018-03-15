package io.github.pauljamescleary.petstore.service

import cats._
import cats.data._
import io.github.pauljamescleary.petstore.model.{Pet, PetStatus}
import io.github.pauljamescleary.petstore.repository.PetRepositoryAlgebra
import io.github.pauljamescleary.petstore.validation.{
  PetNotFoundError,
  PetValidationAlgebra,
  ValidationError
}

import scala.language.higherKinds

/**
  * The entry point to our domain, works with repositories and validations to implement behavior
  * @param repository where we get our data
  * @param validation something that provides validations to the service
  * @tparam F - this is the container for the things we work with, could be scala.concurrent.Future, Option, anything
  *           as long as it is a Monad
  */
class PetService[F[_]](repository: PetRepositoryAlgebra[F], validation: PetValidationAlgebra[F]) {
  import cats.syntax.all._

  def create(pet: Pet)(implicit M: Monad[F]): EitherT[F, ValidationError, Pet] =
    for {
      _ <- validation.doesNotExist(pet)
      saved <- EitherT.liftF(repository.put(pet))
    } yield saved

  /* Could argue that we could make this idempotent on put and not check if the pet exists */
  def update(pet: Pet)(implicit M: Monad[F]): EitherT[F, ValidationError, Pet] =
    for {
      _ <- validation.exists(pet.id)
      saved <- EitherT.liftF(repository.put(pet))
    } yield saved

  def get(id: Long)(implicit M: Monad[F]): EitherT[F, ValidationError, Pet] =
    EitherT {
      repository.get(id).map {
        case None => Left(PetNotFoundError)
        case Some(found) => Right(found)
      }
    }

  /* In some circumstances we may care if we actually delete the pet; here we are idempotent and do not care */
  def delete(id: Long)(implicit M: Monad[F]): F[Unit] =
    repository.delete(id).map(_ => ())

  def list(pageSize: Int, offset: Int): F[List[Pet]] =
    repository.list(pageSize, offset)

  def findByStatus(statuses: NonEmptyList[PetStatus]): F[List[Pet]] =
    repository.findByStatus(statuses)

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]] =
    repository.findByTag(tags)
}

object PetService {
  def apply[F[_]: Monad](repository: PetRepositoryAlgebra[F], validation: PetValidationAlgebra[F]) =
    new PetService[F](repository, validation)
}
