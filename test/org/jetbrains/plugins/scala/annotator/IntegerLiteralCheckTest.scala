package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral


/**
 * @author Ye Xianjin
 * @since  11/27/14
 */
class IntegerLiteralCheckTest extends SimpleTestCase {
  final val Header = "import scala.math.BigInt;"

  def testFine(): Unit = {
  }
  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
      val annotator = new ScalaAnnotator() {}
      val mock = new AnnotatorHolderMock

      val parse: ScalaFile = (Header + code).parse

      parse.depthFirst.foreach {
        case literal: ScLiteral => annotator.annotate(literal, mock)
        case _ =>
      }

      mock.annotations.filter((p: Message) => !p.isInstanceOf[Info])
  }

}
