package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}

import scala.annotation.tailrec

trait ScPattern extends ScalaPsiElement with Typeable {
  final def isIrrefutableFor(scrutineeType: ScType, deep: Boolean = true): Boolean =
    scrutineeType.isNothing || isIrrefutableForImpl(scrutineeType, deep)

  /**
   * Checks whether this pattern will match **all** values of type scrutineeType
   *
   * @param scrutineeType Set of possible values that this pattern should match
   * @param deep Whether to check subpatterns as well
   * @return True if this pattern matches all values of type scrutineeType, false otherwise
   */
  protected def isIrrefutableForImpl(scrutineeType: ScType, deep: Boolean): Boolean

  def bindings: Seq[ScBindingPattern]

  def typeVariables: Seq[ScTypeVariableTypeElement]

  def subpatterns: Seq[ScPattern]

  def analogInDesugaredForExpr: Option[ScPattern]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPattern(this)
  }
}

object ScPattern {
  implicit class Ext(private val pattern: ScPattern) extends AnyVal {
    import pattern.{elementScope, projectContext, thisContext}

    def expectedType: Option[ScType] = {
      // Only a known expected type is cached (`null` is what the cache uses to mark an absent value):
      // the expected type of a pattern may be unknown just because it is requested while the type it
      // depends on is still being inferred, and caching that would make it unknown forever, see SCL-20702.
      Option(
        cachedInUserData("expectedType", pattern, BlockModificationTracker(pattern)) {
          _expectedType.orNull
        }
      )
    }

    // TODO Don't use the return keyword
    private def _expectedType: Option[ScType] = {
      val psiManager = ScalaPsiManager.instance

      pattern.getContext match {
        case list: ScPatternList =>
          list.getContext match {
            case _var: ScVariable => _var.`type`().toOption
            case _val: ScValue    => _val.`type`().toOption
          }
        case argList: ScPatternArgumentList =>
          argList.getContext match {
            case constr: ScExtractorPattern =>
              expectedTypeForExtractorArgPattern(constr, pattern, constr.expectedType)
            case _ => None
          }
        case arg: ScNamedConstructorArgPattern => arg.expectedType
        case composite: ScCompositePattern => composite.expectedType
        case infix: ScInfixPattern => expectedTypeForExtractorArgPattern(infix, pattern, infix.expectedType)
        case par: ScParenthesisedPattern => par.expectedType
        case patternList: ScPatterns => patternList.getContext match {
          case tuple: ScTuplePattern =>
            // In Scala 2
            tuple.infixPatternOfWhichThisIsTheArgPatternList match {
              case Some(infix) => return expectedTypeForExtractorArgPattern(infix, pattern, infix.expectedType)
              case _ =>
            }

            @tailrec
            def handleTupleSubpatternExpectedType(tupleExpectedType: ScType): Option[ScType] =
              tupleExpectedType match {
                case TupleType(comps) =>
                  val idx = patternList.patterns.indexOf(pattern)
                  comps.lift(idx)
                case NamedTupleType(comps) =>
                  val idx = patternList.patterns.indexOf(pattern)
                  comps.lift(idx).map { case (_, ty) => ty }
                case et0 if et0.isAnyRef || et0.isAny => Some(Any)
                case ex: ScExistentialType =>
                  val simplified = ex.simplify()
                  if (simplified != ex) handleTupleSubpatternExpectedType(simplified)
                  else                  None
                case _                                => None
              }

            tuple.expectedType.flatMap(handleTupleSubpatternExpectedType)
          case _: ScXmlPattern =>
            val nodeClass: Option[PsiClass] = psiManager.getCachedClass(elementScope.scope, "scala.xml.Node")
            nodeClass.flatMap { nodeClass =>
              pattern match {
                case SeqExpectingPattern() =>
                  ScDesignatorType(nodeClass).wrapIntoSeqType
                case _ => Some(ScDesignatorType(nodeClass))
              }
            }
          case _ => None
        }
        case comp: ScNamedTuplePatternComponent =>
          val namedTuplePattern = comp.namedTuplePattern
          @tailrec
          def handleNamedTupleSubpatternExpectedType(tupleExpectedType: ScType): Option[ScType] =
            tupleExpectedType match {
              case NamedTupleType(components) =>
                val name = comp.name
                components.collectFirst { case (NamedTupleType.NameType(`name`), ty) => ty }
              case ex: ScExistentialType =>
                val simplified = ex.simplify()
                if (simplified != ex) handleNamedTupleSubpatternExpectedType(simplified)
                else                  None
              case _ =>
                None
            }
          namedTuplePattern.expectedType.flatMap(handleNamedTupleSubpatternExpectedType)
        case clause: ScCaseClause => clause.getContext /*clauses*/ .getContext match {
          case matchStat: ScMatch => matchStat.expression match {
            case Some(e) => Some(e.`type`().getOrAny)
            case _       => None
          }
          case b: ScBlockExpr if b.getContext.is[ScCatchBlock] =>
            val thr = psiManager.getCachedClass(elementScope.scope, "java.lang.Throwable")
            thr.map(ScalaType.designator(_))
          case b: ScBlockExpr =>
            val functionLikeType = FunctionLikeType(b)

            @tailrec
            def extractPtFromFunctionType(tpe: ScType): Option[ScType] = tpe match {
              case functionLikeType(FunctionTypeMarker.ContextFunctionN, tpe, _) => extractPtFromFunctionType(tpe)
              case functionLikeType(_, _, Seq())                                 => Some(api.Unit)
              case functionLikeType(_, _, Seq(p0))                               => Some(p0)
              case functionLikeType(_, _, params)                                => Some(TupleType(params, context = b))
              case _                                                             => None
            }

            b.expectedType(fromUnderscore = false) match {
              case Some(et) => extractPtFromFunctionType(et.removeAbstracts)
              case None     => None
            }
          case _ => None
        }
        case named: ScNamingPattern           => named.expectedType
        case _: ScGenerator                   => pattern.analogInDesugaredForExpr.flatMap(_.expectedType)
        case forBinding: ScForBinding         => forBinding.expr.flatMap(_.`type`().toOption)
        case sc3TypedPattern: Sc3TypedPattern =>
          for {
            typePattern  <- sc3TypedPattern.typePattern
            ascribedType <- typePattern.typeElement.`type`().toOption
          } yield sc3TypedPattern.expectedType.fold(ascribedType)(_.glb(ascribedType))
        case _ => None
      }
    }
    private def expectedTypeForExtractorArgPattern(extractorPattern: ScExtractorPattern,
                                                   pattern: ScPattern,
                                                   expected: Option[ScType]): Option[ScType] = {
      extractorPattern.argPatternsMapping(expected).flatMap(_.typeOfArg(pattern))
    }
  }

  def isQuasiquote(fun: ScFunction): Boolean = {
    val fqnO = Option(fun.containingClass).flatMap(_.qualifiedName.toOption)
    fqnO.exists(fqn => fqn.contains('.') && fqn.substring(0, fqn.lastIndexOf('.')) == "scala.reflect.api.Quasiquotes.Quasiquote")
  }

  def isSeqExpectingPattern(p: ScPattern): Boolean = p match {
    case named: ScNamingPattern => named.getLastChild.is[ScSeqWildcardPattern]
    case _: ScSeqWildcardPattern => true
    case _ => false
  }

  object SeqExpectingPattern {
    def unapply(p: ScPattern): Boolean = isSeqExpectingPattern(p)
  }
}
