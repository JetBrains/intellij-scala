package org.jetbrains.plugins.scala.util;

import scala.tools.ant.*;

/**
 * Author: Ilya Sergey
 * Date: 04.10.2006
 * Time: 16:47:02
 */
public class AntScalaCompiler extends Scalac {

  public void execute() {
    setAddparams("-Xgenerics");
    super.execute();
  }
}
