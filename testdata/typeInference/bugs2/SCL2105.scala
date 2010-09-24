package aasdf

import javax.swing.{JPanel, JButton, JLabel}

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