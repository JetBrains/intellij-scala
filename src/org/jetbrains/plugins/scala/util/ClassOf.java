package org.jetbrains.plugins.scala.util;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.10.2006
 * Time: 15:26:45
 */
public class ClassOf {
  public <T> T cast(Class klass) {
    return (T) klass;
  }
}
