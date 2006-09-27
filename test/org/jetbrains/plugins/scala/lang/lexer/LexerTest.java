package org.jetbrains.plugins.scala.lang.lexer;

import junit.framework.*;

/**
 * Author: Ilya Sergey
 * Date: 27.09.2006
 * Time: 14:46:19
 */
public class LexerTest {

    public static Test suite() {

        TestSuite suite = new TestSuite();

        suite.addTest(new SimpleLexerTest());

        return suite;
    }

    /**
     * Runs the test suite using the textual runner.
     */
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    
}
