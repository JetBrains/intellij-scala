package org.jetbrains.plugins.scala.lang.surroundWith.SurroundWithTests;

import junit.framework.Test;
import com.intellij.lang.surroundWith.Surrounder;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
import org.jetbrains.plugins.scala.lang.surroundWith.SurroundWithTester;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 24.01.2007
 */
public class SurroundWithBracesTester extends SurroundWithTester {
  public static Test suite() {
    return new SurroundWithBracesTester();
  }

  public SurroundWithBracesTester() {
    super(System.getProperty("path") != null ? System.getProperty("path") : "test/org/jetbrains/plugins/scala/lang/surroundWith/data/braces");
  }

  public Surrounder surrounder() {
    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()[0].getSurrounders()[0];
  }
}