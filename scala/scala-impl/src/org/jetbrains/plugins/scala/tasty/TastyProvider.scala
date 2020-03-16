package org.jetbrains.plugins.scala.tasty

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.tasty.model.TastyFile

sealed trait TastyProvider {
  def provide(file: PsiFile): Option[TastyFile]
}

object TastyProvider {

  case object Default extends TastyProvider {
    override def provide(file: PsiFile): Option[TastyFile] =
      for {
        Location(outputDirectory, className) <- compiledLocationOf(file)
        result <- TastyReader.read(outputDirectory, className)
      } yield result
  }

  case class Const(tastyFile: TastyFile) extends TastyProvider {
    override def provide(file: PsiFile): Option[TastyFile] = Some(tastyFile)
  }
}
