package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.scala.lang.formatter.intellij.tests.LineCommentsTestOps

class Scala3FormatterMatchExpressionChainsTest extends Scala3FormatterBaseTest with LineCommentsTestOps {

  def testWithDotsWithBraces_OnlyMatchExpressions(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match {
      |    case _ => 1
      |  }.match {
      |    case _ => 1
      |  }
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }.match {
      |      case _ => 1
      |    }
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .match {
      |      case _ => 1
      |    }
      |""".stripMargin
  )

  def testWithDotsWithBraces_MatchExpressionsWithMethodCalls(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match {
      |    case _ => 1
      |  }.toString
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |    .match {
      |      case _ => 1
      |    }
      |
      |  foo0.toString
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |    .match {
      |      case _ => 1
      |    }
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |
      |  foo0
      |    .match {
      |      case _ => 1
      |    }
      |    .match {
      |      case _ => 1
      |    }
      |    .toString
      |""".stripMargin
  )

  def testWithDotsWithoutBraces_OnlyMatchExpressions(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match
      |    case _ => 1
      |  .match
      |    case _ => 1
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .match
      |      case _ =>
      |        ??? match
      |          case 1 => 11
      |""".stripMargin
  )

  def testWithDotsWithoutBraces_MatchExpressionsWithMethodCalls(): Unit = doTextTestWithLineComments(
    """object wrapper:
      |  foo0.match
      |    case _ => 1
      |  .toString
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .toString
      |    .match
      |      case _ => 1
      |
      |  foo0.toString
      |    .match
      |      case _ => 1
      |    .toString
      |    .match
      |      case _ => 1
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .toString
      |    .match
      |      case _ => 1
      |    .toString
      |
      |  foo0
      |    .match
      |      case _ => 1
      |    .match
      |      case _ => 1
      |    .toString
      |""".stripMargin
  )
}
