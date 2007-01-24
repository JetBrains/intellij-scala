package org.jetbrains.plugins.scala.lang.surroundWith.SurroundWithTests;

import org.jetbrains.plugins.scala.lang.surroundWith.SurroundWithTester;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
import junit.framework.Test;
import com.intellij.lang.surroundWith.Surrounder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 24.01.2007
 */
public class SurroundWithIfTester extends SurroundWithTester {
  public static Test suite() {
    return new SurroundWithIfTester();
  }

  public SurroundWithIfTester() {
    super(System.getProperty("path") != null ? System.getProperty("path") : "test/org/jetbrains/plugins/scala/lang/surroundWith/data/if");
  }

  public Surrounder surrounder() {
    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors()[0].getSurrounders()[2];
  }
}