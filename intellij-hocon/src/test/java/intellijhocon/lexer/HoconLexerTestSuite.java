package intellijhocon.lexer;

import junit.framework.Test;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * Author: ghik
 * Created: 2014-07-13
 */
@RunWith(AllTests.class)
public class HoconLexerTestSuite {
    public static Test suite() {
        return new HoconLexerTest();
    }
}
