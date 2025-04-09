package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType

import scala.annotation.switch

object TypeIntrinsics {
  def apply(designator: ScType, arguments: Seq[ScType]): Option[ScType] = designator match {
    case ScProjectionType.withActual(alias: ScTypeAlias, _) =>
      implicit def project: Project = designator.projectContext.project

      val containingClassName = Option(alias.containingClass).map(_.qualifiedName).orNull

      //TODO: question of de-aliasing/reducing type aliases is more general and complicated
      // For example see SCL-21176 and SCL-20263)
      // For now (in Scala 3.2.1-RC4) it's done for scala.compiletime.ops
      // But ideally it should be done more uniformly.
      // See also: https://github.com/lampepfl/dotty/pull/14586
      lazy val argumentsDealiased = arguments.map(_.removeAliasDefinitions())
      (containingClassName: @switch) match {
        // compiletime.ops
        case "scala.compiletime.ops.any" => CompileTimeOpsIntrinsics.anyOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.boolean" => CompileTimeOpsIntrinsics.booleanOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.int" => CompileTimeOpsIntrinsics.intOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.long" => CompileTimeOpsIntrinsics.longOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.float" => CompileTimeOpsIntrinsics.floatOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.double" => CompileTimeOpsIntrinsics.doubleOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.string" => CompileTimeOpsIntrinsics.stringOp(alias.name, argumentsDealiased)

        // Named tuples
        case "scala.NamedTuple" => NamedTupleIntrinsics.namedTupleOp(alias.name, argumentsDealiased)
        case "scala.Tuple" => TupleIntrinsics.tupleOp(alias.name, argumentsDealiased)
        case "scala.NamedTupleDecomposition" => NamedTupleDecompositionIntrinsics.namedTupleDecompositionOp(alias.name, argumentsDealiased)
        case _ => None
      }
    case _ => None
  }
}
