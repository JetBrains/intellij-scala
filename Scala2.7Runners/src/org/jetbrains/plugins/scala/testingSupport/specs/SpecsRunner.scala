package org.jetbrains.plugins.scala.testingSupport.specs


import org.specs.Specification
import org.specs.util.Classes
import collection.mutable.ArrayBuffer
import org.specs.runner.{RunnerMain, NotifierRunner}
import java.lang.String
import reflect.Manifest

object SpecsRunner {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("The first argument should be the specification class name")
      return
    }
    val classes: ArrayBuffer[String] = new ArrayBuffer[String]
    var sysFilter: String = ".*"
    var exFilter: String = ".*"
    var i: Int = 0
    while (i < args.length) {
      if (args(i).startsWith("-sus:")) {
        sysFilter = args(i).substring(5)
        i = i + 1
      } else if (args(i).equals("-s")) {
        i = i + 1
        while (i < args.length && !args(i).startsWith("-")) {
          classes += args(i)
          i = i + 1
        }
      } else if (args(i).startsWith("-ex:")) {
        exFilter = args(i).substring(4)
        i = i + 1
      } else {
        i = i + 1
      }
    }

    for (clazz <- classes) {
      val option: Option[Specification] = createObject(clazz, true, true)
      option match {
        case Some(s: Specification) => {
          try {
            (new NotifierRunner(s, new SpecsNotifier) {
              override def susFilterPattern: String = sysFilter

              override def exampleFilterPattern: String = exFilter
            }).reportSpecs
          } catch {
            case e: Exception => e.printStackTrace
          }
        }
        case _ => {
          val option: Option[Specification] = createObject(clazz + "$", true, true)
          option match {
            case Some(s: Specification) => {
              try {
                (new NotifierRunner(s, new SpecsNotifier) {
                  override def susFilterPattern: String = sysFilter

                  override def exampleFilterPattern: String = exFilter
                }).reportSpecs
              } catch {
                case e: Exception => e.printStackTrace
              }
            }
            case _ => System.out.println("Scala Plugin internal error: no test class was found")
          }
        }
      }
    }
  }

  //todo: this is copy from Specs library. Should be removed (althougth this class will be removed, after stopping support for scala 2.7)
  def createObject[T <: AnyRef](className: String, printMessage: Boolean, printStackTrace: Boolean)(implicit m: Manifest[T]): Option[T] = {
    try {
      val c = loadClass[T](className, printMessage, printStackTrace)
      return createInstanceOf[T](c)
    } catch {
      case e => {
        if (printMessage || System.getProperty("debugCreateObject") != null) println("Could not instantiate class " + className + ": " + e.getMessage)
        if (printStackTrace || System.getProperty("debugCreateObject") != null) e.printStackTrace()
      }
    }
    return None
  }
  /**
   * create an instance of a given class, checking that the created instance typechecks as expected
   */
  private def createInstanceOf[T <: AnyRef](c: Option[Class[T]])(implicit m: Manifest[T]) = {
    c match {
      case Some(klass) => {
    	val constructor = klass.getDeclaredConstructors()(0)
    	constructor.setAccessible(true)
        val instance: AnyRef = constructor.newInstance().asInstanceOf[AnyRef]
        //if (!m.erasure.isInstance(instance)) error(instance + " is not an instance of " + m.erasure.getName)
        Some(instance.asInstanceOf[T])
      }
      case None => None
    }
  }
  /**
   * Load a class, given the class name
   */
  private def loadClass[T <: AnyRef](className: String, printMessage: Boolean, printStackTrace: Boolean): Option[Class[T]] = {
    try {
      return Some(getClass.getClassLoader.loadClass(className).asInstanceOf[Class[T]])
    } catch {
      case e => {
        if (printMessage || System.getProperty("debugLoadClass") != null) println("Could not load class " + className)
        if (printStackTrace || System.getProperty("debugLoadClass") != null) e.printStackTrace()
      }
    }
    return None
  }
}
