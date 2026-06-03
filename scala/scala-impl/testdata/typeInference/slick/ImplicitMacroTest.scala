object ImplicitMacroTest {
  val profile = slick.jdbc.MySQLProfile
  import profile.api._

  case class PointRowId(innerId: Int) extends MappedTo[Int] {
    def value(): Int = innerId
  }

  case class PointRow(id: PointRowId, x: Double, y: Double)

  class PointRowTable(_tableTag: Tag) extends Table[PointRow](_tableTag, "point") {
    def * = (id, x, y) <> (PointRow.tupled, PointRow.unapply)

    def ? = (Rep.Some(id), Rep.Some(x), Rep.Some(y)).shaped.<>(
      r => r._1.map(_ => PointRow.tupled((r._1.get, r._2.get, r._3.get))),
      (_: Any) => throw new Exception()
    )

    //requires an implicit macro "MappedToBase.mappedToIsomorphism"
    val id = /*start*/column[PointRowId]("id", O.AutoInc, O.PrimaryKey)/*end*/
    val x = column[Double]("x")
    val y = column[Double]("y")
  }
}
//Rep[ImplicitMacroTest.PointRowId]