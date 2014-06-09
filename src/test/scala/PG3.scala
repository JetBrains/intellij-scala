import com.typesafe.config.ConfigFactory

object PG3 {
  def main(args: Array[String]) {
    println(ConfigFactory.parseString(
      """
        |a = 5
        |b = ${a}
        |
      """.stripMargin).resolve.root.render)

  }

}
