package io.github.pauljamescleary.petstore.repository

import cats.data.NonEmptyList
import io.github.pauljamescleary.petstore.model.{Pet, PetStatus}

import scala.language.higherKinds

trait PetRepositoryAlgebra[F[_]] {

  def put(pet: Pet): F[Pet]

  def get(id: Long): F[Option[Pet]]

  def delete(id: Long): F[Option[Pet]]

  def findByNameAndCategory(name: String, category: String): F[Set[Pet]]

  def list(pageSize: Int, offset: Int): F[List[Pet]]

  def findByStatus(status: NonEmptyList[PetStatus]): F[List[Pet]]

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]]
}
