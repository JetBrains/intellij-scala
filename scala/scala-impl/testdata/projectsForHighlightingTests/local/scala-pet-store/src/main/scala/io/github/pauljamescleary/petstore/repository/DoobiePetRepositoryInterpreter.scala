package io.github.pauljamescleary.petstore.repository

import io.github.pauljamescleary.petstore.model._
import doobie._, doobie.implicits._
import cats._, cats.data._, cats.implicits._

class DoobiePetRepositoryInterpreter[F[_]: Monad](val xa: Transactor[F])
    extends PetRepositoryAlgebra[F] {

  /* We require type StatusMeta to handle our ADT Status */
  private implicit val StatusMeta: Meta[PetStatus] =
    Meta[String].xmap(PetStatus.apply, PetStatus.nameOf)

  /* This is used to marshal our sets of strings */
  private implicit val SetStringMeta: Meta[Set[String]] = Meta[String]
    .xmap(str => str.split(',').toSet, strSet => strSet.mkString(","))

  def put(pet: Pet): F[Pet] = {
    val insert: ConnectionIO[Pet] =
      for {
        id <- sql"REPLACE INTO PET (NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS) values (${pet.name}, ${pet.category}, ${pet.bio}, ${pet.status}, ${pet.tags}, ${pet.photoUrls})".update
          .withUniqueGeneratedKeys[Long]("ID")
      } yield pet.copy(id = Some(id))
    insert.transact(xa)
  }

  def get(id: Long): F[Option[Pet]] =
    sql"""
      SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
        FROM PET
       WHERE ID = $id
     """.query[Pet].option.transact(xa)

  def delete(id: Long): F[Option[Pet]] =
    get(id).flatMap {
      case Some(pet) =>
        sql"DELETE FROM PET WHERE ID = $id".update.run
          .transact(xa)
          .map(_ => Some(pet))
      case None =>
        none[Pet].pure[F]
    }

  def findByNameAndCategory(name: String, category: String): F[Set[Pet]] =
    sql"""SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
            FROM PET
           WHERE NAME = $name AND CATEGORY = $category
           """.query[Pet].list.transact(xa).map(_.toSet)

  def list(pageSize: Int, offset: Int): F[List[Pet]] =
    sql"""SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
            FROM PET
            ORDER BY NAME LIMIT $offset,$pageSize"""
      .query[Pet]
      .list
      .transact(xa)

  def findByStatus(statuses: NonEmptyList[PetStatus]): F[List[Pet]] =
    (sql"""SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
            FROM PET
           WHERE """ ++ Fragments.in(fr"STATUS", statuses))
      .query[Pet]
      .list
      .transact(xa)

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]] = {
    /* Handle dynamic construction of query based on multiple parameters */

    /* To piggyback off of comment of above reference about tags implementation, findByTag uses LIKE for partial matching
    since tags is (currently) implemented as a comma-delimited string */
    val tagLikeString: String = tags.toList.mkString("TAGS LIKE '%", "%' OR TAGS LIKE '%", "%'")
    (sql"""SELECT NAME, CATEGORY, BIO, STATUS, TAGS, PHOTO_URLS, ID
         FROM PET
         WHERE """ ++ Fragment.const(tagLikeString))
      .query[Pet]
      .list
      .transact(xa)
  }
}

object DoobiePetRepositoryInterpreter {
  def apply[F[_]: Monad](xa: Transactor[F]): DoobiePetRepositoryInterpreter[F] =
    new DoobiePetRepositoryInterpreter(xa)
}
