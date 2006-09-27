package org.jetbrains.plugins.scala.lang.lexer;

import junit.framework.*;

import java.io.*;
import java.nio.CharBuffer;

import org.jetbrains.annotations.NonNls;
import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

/**
 * Author: Ilya Sergey
 * Date: 27.09.2006
 * Time: 14:35:43
 */
public class SimpleLexerTest extends TestCase {

    protected FileReader inputFile;
    @NonNls
    private static final String PATH = "test/org/jetbrains/plugins/scala/lang/lexer/";
    protected ScalaLexer scalaLexer;
    protected String file = "";


    protected void setUp() {

        try {
            inputFile = new FileReader(PATH + "firstTest.scala");
            int curChar;
            while ( (curChar = inputFile.read()) != -1){
                file += (char)curChar;
            }
            //System.out.println(file);

            scalaLexer = new ScalaLexer();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void testInput() throws IOException {

        scalaLexer.start(file.toCharArray());

        IElementType elem;
        while ( (elem = scalaLexer.getTokenType()) != null){
            if ( !"stub".equals(elem.toString()) ) {
                System.out.println(elem.toString());
            }
            scalaLexer.advance();
        }
    }

}
