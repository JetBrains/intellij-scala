package intellijhocon.parser;

import junit.framework.Test;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class HoconParserTestSuite {
  public static Test suite() {
    return new HoconParserTest();
  }
}
