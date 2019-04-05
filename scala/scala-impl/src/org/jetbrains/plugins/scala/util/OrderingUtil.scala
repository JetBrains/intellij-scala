package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.text.StringUtil

object OrderingUtil {
  trait NaturalStringOrdering extends Ordering[String] {
    def compare(x: String, y: String): Int = StringUtil.naturalCompare(x, y)
  }

  trait PackageNameOrdering extends Ordering[String] {
    def compare(x: String, y: String): Int = {
      import implicits.NaturalStringOrdering

      implicitly[Ordering[Tuple2[String, String]]]
        .compare(splitAtPoint(x), splitAtPoint(y))
    }

    private def splitAtPoint(str: String): (String, String) = {
      val i = str.lastIndexOf('.')
      if (i >= 0) (str.substring(0, i), str.substring(i + 1))
      else ("", str)
    }
  }

  object implicits {
    implicit object NaturalStringOrdering extends NaturalStringOrdering
    implicit object PackageNameOrdering extends PackageNameOrdering
  }
}
