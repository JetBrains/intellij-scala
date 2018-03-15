package io.github.pauljamescleary.petstore.validation

import cats._
import cats.implicits._
import cats.data.EitherT
import io.github.pauljamescleary.petstore.model.Pet
import io.github.pauljamescleary.petstore.repository.PetRepositoryAlgebra

class PetValidationInterpreter[F[_]: Monad](repository: PetRepositoryAlgebra[F])
    extends PetValidationAlgebra[F] {

  def doesNotExist(pet: Pet): EitherT[F, ValidationError, Unit] = EitherT {
    repository.findByNameAndCategory(pet.name, pet.category).map { matches =>
      if (matches.forall(possibleMatch => possibleMatch.bio != pet.bio)) {
        Right(())
      } else {
        Left(PetAlreadyExistsError(pet))
      }
    }
  }

  def exists(petId: Option[Long]): EitherT[F, ValidationError, Unit] =
    EitherT {
      petId match {
        case Some(id) =>
          // Ensure is a little tough to follow, it says "make sure this condition is true, otherwise throw the error specified
          // In this example, we make sure that the option returned has a value, otherwise the pet was not found
          repository.get(id).map {
            case Some(_) => Right(())
            case _ => Left(PetNotFoundError)
          }

        case _ =>
          Either.left[ValidationError, Unit](PetNotFoundError).pure[F]
      }
    }
}

object PetValidationInterpreter {
  def apply[F[_]: Monad](repository: PetRepositoryAlgebra[F]) =
    new PetValidationInterpreter[F](repository)
}
