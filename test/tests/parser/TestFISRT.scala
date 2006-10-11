package tests.examples

import junit.framework._
import Assert._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.PrefixExpression
import org.jetbrains.plugins.scala.lang.parser.bnf._
import org.jetbrains.plugins.scala.lang.lexer._
import com.intellij.psi.tree.TokenSet, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

class TestFIRST extends TestCase {
  def testFIRST = {

    val ts1 = BNF.tPREFIXES
    val ts2 = BNF.tLITERALS
    val ts3 = TokenSet.orSet(Array(BNF.tLITERALS , BNF.tPREFIXES ))

    assertTrue(ts1.contains(ScalaTokenTypes.tPLUS))
    assertTrue(ts2.contains(ScalaTokenTypes.tINTEGER))

    assertTrue(ts3.contains(ScalaTokenTypes.tPLUS) &&
               ts3.contains(ScalaTokenTypes.tINTEGER))
  }
}