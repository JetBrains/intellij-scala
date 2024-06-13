package org.jetbrains.plugins.scala.compiler.data

final case class DocumentCompilationArguments(
  sbtData: SbtData,
  compilerData: CompilerData,
  compilationData: DocumentCompilationData
)

object DocumentCompilationArguments {

  def serialize(arguments: DocumentCompilationArguments): Seq[String] = {
    val DocumentCompilationArguments(sbtData, compilerData, compilationData) = arguments
    SbtData.serialize(sbtData) ++ CompilerData.serialize(compilerData) ++ DocumentCompilationData.serialize(compilationData)
  }

  def deserialize(strings: Seq[String]): Either[String, DocumentCompilationArguments] =
    SbtData.deserialize(strings).flatMap { case (sbtData, tail1) =>
      CompilerData.deserialize(tail1).flatMap { case (compilerData, tail2) =>
        DocumentCompilationData.deserialize(tail2).map { compilationData =>
          DocumentCompilationArguments(sbtData, compilerData, compilationData)
        }
      }
    }
}
