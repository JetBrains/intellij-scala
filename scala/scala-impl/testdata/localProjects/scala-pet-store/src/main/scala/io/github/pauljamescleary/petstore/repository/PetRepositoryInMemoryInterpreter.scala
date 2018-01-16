package io.github.pauljamescleary.petstore.repository

import cats._
import cats.implicits._
import cats.data.NonEmptyList
import io.github.pauljamescleary.petstore.model.{Pet, PetStatus}

import scala.collection.concurrent.TrieMap
import scala.util.Random

class PetRepositoryInMemoryInterpreter[F[_]: Applicative] extends PetRepositoryAlgebra[F] {

  private val cache = new TrieMap[Long, Pet]

  private val random = new Random

  def put(pet: Pet): F[Pet] = {
    val toSave =
      if (pet.id.isDefined) pet else pet.copy(id = Some(random.nextLong))

    toSave.id.foreach { cache.put(_, toSave) }
    toSave.pure[F]
  }

  def get(id: Long): F[Option[Pet]] = cache.get(id).pure[F]

  def delete(id: Long): F[Option[Pet]] = cache.remove(id).pure[F]

  def findByNameAndCategory(name: String, category: String): F[Set[Pet]] =
    cache.values
      .filter(p => p.name == name && p.category == category)
      .toSet
      .pure[F]

  def list(pageSize: Int, offset: Int): F[List[Pet]] =
    cache.values.toList.sortBy(_.name).slice(offset, offset + pageSize).pure[F]

  def findByStatus(statuses: NonEmptyList[PetStatus]): F[List[Pet]] =
    cache.values.filter(p => statuses.exists(_ == p.status)).toList.pure[F]

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]] =
    cache.values.filter(p => tags.exists(_ == p.tags)).toList.pure[F]
}

object PetRepositoryInMemoryInterpreter {
  def apply[F[_]: Applicative]() = new PetRepositoryInMemoryInterpreter[F]()
}
