import java.sql.Date
import java.time.LocalDate

import SlickMappers._
import slick.jdbc.PostgresProfile.api._

case class Alert(alertType: String, date: LocalDate, status: Status, id: Long)

case class Status(label: String)

object SlickMappers {

  implicit val jtDateColumnType = MappedColumnType.base[LocalDate, Date](
    d => Date.valueOf(d),
    d => d.toLocalDate
  )

  implicit val statusColumnType = MappedColumnType.base[Status, String](
    status => status.label,
    value => Status(value)
  )
}

object AlertDAO {

  class AlertTable(tag: Tag) extends Table[Alert](tag, "alert") {
    def id =         column[Long]      ("id", O.AutoInc, O.PrimaryKey)
    def alertType =  column[String]    ("type")
    def date =       column[LocalDate] ("date")
    def status =     column[Status]    ("status")

    def * = /*start*/(id, alertType, date, status).shaped <> (
      { case (id, alertType, date, status) =>
        Alert(alertType, date, status, id)
      } ,
      { a: Alert =>
        Some(a.id, a.alertType, a.date, a.status)
      })/*end*/
  }

}
//ProvenShape[Alert]