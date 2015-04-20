package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._

class TreeConverterIntegrationTest extends TreeConverterTestBase {


  def testSomeBigClass() {
    val v = convert(
    """
      |//start
      |class StableCodeReferenceElementResolver(reference: ResolvableStableCodeReferenceElement, shapeResolve: Boolean,
      |                                          allConstructorResults: Boolean, noConstructorResolve: Boolean)
      |        extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElement] {
      |  def resolve(ref: ScStableCodeReferenceElement, incomplete: Boolean) = {
      |    val kinds = ref.getKinds(incomplete = false)
      |
      |    val proc = if (ref.isConstructorReference && !noConstructorResolve) {
      |      val constr = ref.getConstructor.get
      |      val typeArgs = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
      |      val effectiveArgs = constr.arguments.toList.map(_.exprs.map(new Expression(_))) match {
      |        case List() => List(List())
      |        case x => x
      |      }
      |      new ConstructorResolveProcessor(ref, ref.refName, effectiveArgs, typeArgs, kinds, shapeResolve, allConstructorResults)
      |    } else ref.getContext match {
      |      //last ref may import many elements with the same name
      |      case e: ScImportExpr if e.selectorSet == None && !e.singleWildcard =>
      |        new CollectAllForImportProcessor(kinds, ref, reference.refName)
      |      case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, ref, reference.refName)
      |      case _: ScImportSelector => new CollectAllForImportProcessor(kinds, ref, reference.refName)
      |      case constr: ScInterpolationPattern =>
      |        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      |      case constr: ScConstructorPattern =>
      |        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      |      case infix: ScInfixPattern => new ExtractorResolveProcessor(ref, reference.refName, kinds, infix.expectedType)
      |      case _ => new ResolveProcessor(kinds, ref, reference.refName)
      |    }
      |
      |    reference.doResolve(ref, proc)
      |  }
      |}
    """.stripMargin
    )
    "sdsd"
  }
}
