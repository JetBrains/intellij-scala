package org.jetbrains

import com.intellij.util.{Function => IdeaFunction, PathUtil}
import com.intellij.openapi.util.{Pair => IdeaPair}

import reflect.ClassTag

/**
 * @author Pavel Fatin
 */
package object sbt {
  implicit def toIdeaFunction1[A, B](f: A => B): IdeaFunction[A, B] = new IdeaFunction[A, B] {
    def fun(a: A) = f(a)
  }

  implicit def toIdeaFunction2[A, B, C](f: (A, B) => C): IdeaFunction[IdeaPair[A, B], C] = new IdeaFunction[IdeaPair[A, B], C] {
    def fun(pair: IdeaPair[A, B]) = f(pair.getFirst, pair.getSecond)
  }

  def jarWith[T : ClassTag]: String = {
    val tClass = implicitly[ClassTag[T]].runtimeClass

    Option(PathUtil.getJarPathForClass(tClass)).getOrElse {
      throw new RuntimeException("Jar file not found for class " + tClass.getName)
    }
  }
}
