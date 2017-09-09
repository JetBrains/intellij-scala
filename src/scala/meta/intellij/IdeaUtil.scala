package scala.meta.intellij

import com.intellij.openapi.project.DumbService
import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor

object IdeaUtil {
  def safeAnnotationResolve(annotation: ScAnnotation): Option[ResolveResult] = {
    if (ScStubElementType.isStubBuilding || DumbService.isDumb(annotation.getProject))
      return None
    annotation.constructor.reference.flatMap {
      case stRef: ScStableCodeReferenceElementImpl =>
        val processor = new ResolveProcessor(stRef.getKinds(incomplete = false), stRef, stRef.refName)
        stRef.doResolve(processor).headOption
      case _ => None
    }
  }
}
