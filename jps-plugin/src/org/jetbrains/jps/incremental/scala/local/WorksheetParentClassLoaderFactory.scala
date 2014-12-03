package org.jetbrains.jps.incremental.scala.local

import java.net.{URLClassLoader, URL}

/**
 * User: Dmitry.Naydanov
 * Date: 03.12.14.
 */
class WorksheetParentClassLoaderFactory {
  private var classLoader: Option[(Set[URL], Set[URL], URLClassLoader)] = None

  private def createClassLoader(compilerUrls: Set[URL], classpathUrls: Set[URL]) = {
    val loader = new URLClassLoader((compilerUrls ++ classpathUrls).toArray, null)
    classLoader = Some((compilerUrls, classpathUrls, loader))
    loader
  }

  def getClassLoader(compilerUrls: Seq[URL], classpathUrls: Seq[URL]) = {
    val compilerSet = compilerUrls.toSet
    val classpathSet = classpathUrls.toSet

    classLoader match {
      case Some((urls1, urls2, loader)) =>
        if (compilerSet == urls1 && classpathSet == urls2) loader else createClassLoader(compilerSet, classpathSet)
      case _ => createClassLoader(compilerSet, classpathSet)
    }
  }
}
