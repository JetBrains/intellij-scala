package tests;

import junit.framework.TestSuite;
import junit.framework.Test;
//import tests.examples.TestExample;

/**
 * Author: Ilya Sergey
 * Date: 03.10.2006
 * Time: 18:57:06
 */
public class MainTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite();

        //suite.addTestSuite(TestExample.class);
      
        return suite;
    }

    /**
     * Runs the test suite using the textual runner.
     */
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
  
}
