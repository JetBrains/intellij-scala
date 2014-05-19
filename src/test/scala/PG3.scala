import com.typesafe.config.ConfigFactory

object PG3 {
  def main(args: Array[String]) {
    println(ConfigFactory.parseString(
      """
        [a, b, c, d]

      """.stripMargin).resolve.root.render)

  }

}
