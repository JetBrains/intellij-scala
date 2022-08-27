package io.github.pauljamescleary.petstore.validation

import cats.data.EitherT
import io.github.pauljamescleary.petstore.model.Pet

import scala.language.higherKinds

sealed trait ValidationError extends Product with Serializable
final case class PetAlreadyExistsError(pet: Pet) extends ValidationError
final case object PetNotFoundError extends ValidationError

trait PetValidationAlgebra[F[_]] {

  /* Fails with a PetAlreadyExistsError */
  def doesNotExist(pet: Pet): EitherT[F, ValidationError, Unit]

  /* Fails with a PetNotFoundError if the pet id does not exist or if it is none */
  def exists(petId: Option[Long]): EitherT[F, ValidationError, Unit]
}
