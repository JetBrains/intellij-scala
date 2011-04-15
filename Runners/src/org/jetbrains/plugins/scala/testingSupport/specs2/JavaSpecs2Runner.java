package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.specs.JavaSpecsNotifier;
import org.jetbrains.plugins.scala.testingSupport.specs.JavaSpecsRunner;
import org.specs.Specification;
import org.specs2.runner.NotifierRunner;
import scala.Option;
import scala.reflect.Manifest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Runner {
  public static void main(String[] args) {
    NotifierRunner runner = new NotifierRunner(new JavaSpecs2Notifier());
    runner.main(args);
  }
}
