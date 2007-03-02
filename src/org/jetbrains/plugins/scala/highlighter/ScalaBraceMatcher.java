package org.jetbrains.plugins.scala.highlighter;

import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.BracePair;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

/**
 * Author: Ilya Sergey
 * Date: 29.09.2006
 * Time: 20:26:52
 */
public class ScalaBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = new BracePair[]{
          new BracePair('(', ScalaTokenTypes.tLPARENTHESIS, ')', ScalaTokenTypes.tRPARENTHESIS, false),
          new BracePair('[', ScalaTokenTypes.tLSQBRACKET, ']', ScalaTokenTypes.tRSQBRACKET, false),
          new BracePair('{', ScalaTokenTypes.tLBRACE, '}', ScalaTokenTypes.tRBRACE, true)
  };

  public BracePair[] getPairs() {
    
    return PAIRS;
  }
}
