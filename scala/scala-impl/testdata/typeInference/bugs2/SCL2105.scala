package aasdf

trait SwingConstants
trait Accessible
class JComponent
class JPanel extends JComponent with Accessible
class AbstractButton extends JComponent with SwingConstants
class JButton extends AbstractButton with Accessible
class JLabel extends JComponent with SwingConstants with Accessible

object Test
{
  def main(args: Array[String]) {
    val whatMightItBe = "string" match {
      case "hello" => new JLabel
      case "world" => new JButton
      case "string" => new JPanel
      case _ => null
    }
    /*start*/whatMightItBe/*end*/
  }
}
//JComponent with Accessible