package org.jetbrains.plugins.scala.lang.formatter.tests;

import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.formatter.FormatterTest;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class ScalaDocFormatterTest extends FormatterTest {
  public static Test suite() {
    return new ScalaDocFormatterTest();
  }

  public ScalaDocFormatterTest() {
    super("/formatter/scalaDocData/");
  }
}
