import com.typesafe.config.ConfigFactory

object PG3 {
  def main(args: Array[String]) {
    println(ConfigFactory.parseString(
      "a = [{lol: 5}, 4, jklasdf]").resolve.root.render)

  }

}
