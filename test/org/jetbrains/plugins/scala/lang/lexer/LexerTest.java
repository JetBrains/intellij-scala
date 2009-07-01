/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

/**
 * @author ilyas
 */
public class LexerTest extends BaseScalaFileSetTestCase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/lexer/data";

  public LexerTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }


  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];

    Lexer lexer = new ScalaLexer();
    lexer.start(fileText.toCharArray());

    StringBuffer buffer = new StringBuffer();

    IElementType type;
    while ((type = lexer.getTokenType()) != null) {
      CharSequence s = lexer.getBufferSequence();
      s = s.subSequence(lexer.getTokenStart(), lexer.getTokenEnd());
      buffer.append(type.toString()).append(" {").append(s).append("}");
      lexer.advance();
      if (lexer.getTokenType() != null) {
        buffer.append("\n");
      }
    }

    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(buffer.toString());
    System.out.println("");

    return buffer.toString();

  }

  public static Test suite() {
    return new LexerTest();
  }


}
