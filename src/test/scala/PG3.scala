import com.typesafe.config.ConfigFactory

object PG3 {
  def main(args: Array[String]) {
    println(ConfigFactory.parseString(
      """
        lol = 5,
        fuu: ${lol},
        haha = [a
        ,b]


      """.stripMargin).resolve.root.render)

  }

}
