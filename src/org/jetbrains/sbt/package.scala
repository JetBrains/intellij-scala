package org.jetbrains

import com.intellij.util.{Function => IdeaFunction, PathUtil}
import com.intellij.openapi.util.{Pair => IdeaPair}

import reflect.ClassTag
import java.io.File
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import java.lang.{Boolean => JavaBoolean}

/**
 * @author Pavel Fatin
 */
package object sbt {
  implicit def toIdeaFunction1[A, B](f: A => B): IdeaFunction[A, B] = new IdeaFunction[A, B] {
    def fun(a: A) = f(a)
  }

  implicit def toIdeaPredicate[A](f: A => Boolean): IdeaFunction[A, JavaBoolean] = new IdeaFunction[A, JavaBoolean] {
    def fun(a: A) = JavaBoolean.valueOf(f(a))
  }

  implicit def toIdeaFunction2[A, B, C](f: (A, B) => C): IdeaFunction[IdeaPair[A, B], C] = new IdeaFunction[IdeaPair[A, B], C] {
    def fun(pair: IdeaPair[A, B]) = f(pair.getFirst, pair.getSecond)
  }

  implicit class RichFile(file: File) {
    def /(path: String): File = new File(file, path)

    def `<<`: File = file.getParentFile

    def path: String = file.getPath

    def absolutePath: String = file.getAbsolutePath

    def canonicalPath = ExternalSystemApiUtil.toCanonicalPath(file.getAbsolutePath)
  }

  implicit class RichString(path: String) {
    def toFile: File = new File(path)
  }

  def jarWith[T : ClassTag]: String = {
    val tClass = implicitly[ClassTag[T]].runtimeClass

    Option(PathUtil.getJarPathForClass(tClass)).getOrElse {
      throw new RuntimeException("Jar file not found for class " + tClass.getName)
    }
  }
}
