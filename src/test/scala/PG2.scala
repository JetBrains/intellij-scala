import intellijhocon.HoconLexer
import scala.io.Source
import com.typesafe.config.ConfigFactory

object PG2 {
  def main(args: Array[String]) {
    val hl = new HoconLexer
    val confFile = Source.fromURL(getClass.getResource("/test.conf")).getLines().mkString("\n")
    println(ConfigFactory.parseString(confFile).root.render)
    hl.start(confFile)
    while (hl.getTokenType != null) {
      println(s"${hl.getTokenType}: [${hl.getTokenText.replaceAllLiterally("\n", "\\n")}]")
      hl.advance()
    }
  }
}
