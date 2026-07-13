package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.psi.{JavaPsiFacade, PsiModifier}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}

class JavaHighlightingSCL20875Test extends JavaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader(("com.twitter" % "finagle-core_2.13" % "22.7.0").transitive())

  def testInheritedCompanionMethodFromCompiledScalaLibrary(): Unit = {
    assertNoErrorsInJava(
      """
        |package SJPG1;
        |
        |import com.twitter.finagle.Dtab;
        |
        |public class Japp {
        |    public static void main(String[] args) {
        |        System.out.println(Dtab.read(""));
        |    }
        |}
        |""".stripMargin,
      "Japp"
    )

    val dtab = JavaPsiFacade.getInstance(getProject)
      .findClass("com.twitter.finagle.Dtab", GlobalSearchScope.allScope(getProject))
    assertNotNull("Could not resolve com.twitter.finagle.Dtab", dtab)

    val readMethod = dtab.findMethodsByName("read", false)
      .find { method =>
        val parameters = method.getParameterList.getParameters
        parameters.length == 1 && parameters(0).getType.equalsToText("java.lang.String")
      }
      .orNull
    assertNotNull("Could not resolve Dtab.read(String)", readMethod)
    assertTrue("Dtab.read must be public", readMethod.hasModifierProperty(PsiModifier.PUBLIC))
    assertTrue("Dtab.read must be static", readMethod.hasModifierProperty(PsiModifier.STATIC))
    assertEquals("com.twitter.finagle.Dtab", readMethod.getContainingClass.getQualifiedName)
    assertEquals("com.twitter.finagle.Dtab", readMethod.getReturnType.getCanonicalText)
  }
}
