package intellijhocon.formatting;

import junit.framework.Test;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class HoconFormatterTestSuite {
    public static Test suite() {
        return new HoconFormatterTest();
    }
}
