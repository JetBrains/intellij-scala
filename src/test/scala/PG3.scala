import com.typesafe.config.ConfigFactory

object PG3 {
  def main(args: Array[String]) {
    println(ConfigFactory.parseString(
      """
        a = 111111111111111

      """.stripMargin).resolve.root.render)

  }

}
