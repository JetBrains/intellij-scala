object SCL9261 extends App {
  import slick.driver.H2Driver.api._
  import slick.lifted.AbstractTable

  trait HasId[U, P] {
    type Id = U
    type IdColumn = P

    def id: IdColumn
  }
  type HasId1[T] = HasId[T, Rep[T]]

  class B[T <: AbstractTable[_], U, P](cons: Tag => T with HasId[U, P])(
    implicit
    ushape: Shape[ColumnsShapeLevel, U, U, _],
    pshape: Shape[ColumnsShapeLevel, P, U, P]
  ) extends TableQuery(cons) {
  }

  class T(tag: Tag) extends Table[(Long, String)](tag, "test") with HasId1[Long] {
    def id = column[Long]("id")
    def value = column[String]("value")
    def * = (id, value)
  }

  object Q extends B(new T(_)) {
  }


  /*start*/Q.map(t => t.value)/*end*/
}
//Query[Rep[String], String, Seq]