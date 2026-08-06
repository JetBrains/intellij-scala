package org.jetbrains.plugins.scala.lang.types.kindProjector

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.types.utils.ScPsiElementAssertionTestBase

import java.nio.file.Path

abstract class KindProjectorTestBase extends ScPsiElementAssertionTestBase[ScParameterizedTypeElement] with KindProjectorSetUp {
  override def folderPath: Path = super.folderPath / "types" / "kindProjector"

  override def computeRepresentation(t: ScParameterizedTypeElement): Either[String, String] = {
    t.computeDesugarizedType match {
      case Some(tp) => Right(tp.getText)
      case _        => Left("Projection type not created from parameterized type")
    }
  }
}
