object SCL8556 {

  import com.wda.sdbc.PostgreSql._

  implicit val connection: Connection = ???
  //
  Update("").<ref>execute() //can't resolve

  Update("").on("" -> 0) //can't resolve

  Update("").executeUpdate() //ok
}