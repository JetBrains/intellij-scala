package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialType, ScType}

import scala.annotation.{switch, tailrec}

object TypeIntrinsics {
  def apply(designator: ScType, arguments: Seq[ScType], substitutor: ScSubstitutor): Option[ScType] = designator match {
    case ScProjectionType.withActual(alias: ScTypeAlias, _) =>
      implicit def project: Project = designator.projectContext.project

      val containingClassName = Option(alias.containingClass).map(_.qualifiedName).orNull

      //TODO: question of de-aliasing/reducing type aliases is more general and complicated
      // For example see SCL-21176 and SCL-20263)
      // For now (in Scala 3.2.1-RC4) it's done for scala.compiletime.ops
      // But ideally it should be done more uniformly.
      // See also: https://github.com/lampepfl/dotty/pull/14586
      @tailrec
      def dealias(ty: ScType): ScType = substitutor(ty.removeAliasDefinitions()) match {
        case ScDesignatorType(ty: Typeable) if ty.is[ScBindingPattern, ScParameter, ScFieldId] =>
          dealias(ty.`type`().getOrNothing)
        //case undef: UndefinedType => dealias(undef.typeParameter.lowerType)
        case ScExistentialType(ty, _) => dealias(ty)
        case ty => ty
      }
      lazy val argumentsDealiased = arguments.map(dealias)
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
        case "scala.Tuple" => TupleIntrinsics.tupleOp(alias.name, argumentsDealiased, arguments)
        case "scala.NamedTupleDecomposition" => NamedTupleDecompositionIntrinsics.namedTupleDecompositionOp(alias.name, argumentsDealiased)
        case _ => None
      }
    case _ => None
  }
}
