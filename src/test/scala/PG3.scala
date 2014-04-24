import com.typesafe.config.ConfigFactory

object PG3 {
  def main(args: Array[String]) {
    println(ConfigFactory.parseString(".ajskdlf = 5").resolve.root.render)

  }

}
