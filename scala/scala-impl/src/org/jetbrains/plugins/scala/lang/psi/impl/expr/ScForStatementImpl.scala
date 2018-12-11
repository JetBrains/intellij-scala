package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScForStatementImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScForStatement {

  def isYield: Boolean = findChildByType[PsiElement](ScalaTokenTypes.kYIELD) != null

  def enumerators: Option[ScEnumerators] = findChild(classOf[ScEnumerators])

  // Binding patterns in reverse order
  def patterns: Seq[ScPattern] = enumerators.toSeq.flatMap(_.patterns)

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {

    val enumerators: ScEnumerators = this.enumerators match {
      case None => return true
      case Some(x) => x
    }
    if (lastParent == enumerators) return true
    enumerators.processDeclarations(processor, state, null, place)
  }

  protected def bodyToText(expr: ScExpression): String = expr.getText

  def generateDesugarizedExprWithPatternMapping(forDisplay: Boolean): Option[(ScExpression, Map[ScPattern, ScPattern])] = {
    def findPatternElement(e: PsiElement, org: ScPattern): Option[ScPattern] = {
      Some(e) flatMap {
        case pattern: ScPattern if pattern.getTextLength == org.getTextLength =>
          Some(pattern)
        case _ if e.getTextLength <= org.getTextLength =>
          findPatternElement(e.getParent, org)
        case p =>
          None
      }
    }

    generateDesugarizedExprTextWithPatternMapping(forDisplay) flatMap {
      case (desugaredText, mapping) =>
        try {
          ScalaPsiElementFactory.createOptionExpressionWithContextFromText(desugaredText, this.getContext, this) map {
            expr =>
              lazy val patternMapping = mapping.flatMap {
                case (originalPattern, idx) =>
                  Option(expr.findElementAt(idx)) flatMap { findPatternElement(_, originalPattern) } map {
                    p => originalPattern -> p
                  }

              }.toMap
              expr -> (if (forDisplay) Map.empty else patternMapping)
          }
        } catch {
          case p: ProcessCanceledException => throw p
          case _: Throwable => None
        }
    }
  }

  private def generateDesugarizedExprTextWithPatternMapping(forDisplay: Boolean): Option[(String, Seq[(ScPattern, Int)])] = {
    var _nextNameIdx = 0
    def newNameIdx(): String = {
      _nextNameIdx += 1
      if (forDisplay) _nextNameIdx.toString else "forIntellij" + _nextNameIdx
    }
    val `=>` = ScalaPsiUtil.functionArrow(getProject)

    val underscores = ScUnderScoreSectionUtil.underscores(this).zipWithIndex.toMap
    def underscoreName(i: Int): String = s"forAnonParam$$$i"

    def allUnderscores(expr: PsiElement): Seq[ScUnderscoreSection] = {
      expr match {
        case underscore: ScUnderscoreSection => Seq(underscore)
        case _ => expr.getChildren.flatMap(allUnderscores)
      }
    }

    def normalizeUnderscores(expr: ScExpression): ScExpression = {
      expr match {
        case underscore: ScUnderscoreSection =>
          underscores.
            get(underscore).
            map(underscoreName).
            map(ScalaPsiElementFactory.createReferenceExpressionFromText).
            getOrElse(underscore)
        case _ =>
          allUnderscores(expr) map {
            underscores.get
          } match {
            case underscoreIndices if !underscoreIndices.exists(_.isDefined) =>
              expr
            case underscoreIndices =>
              val copyOfExpr = expr.copy().asInstanceOf[ScExpression]
              val indices = underscoreIndices
              for {
                (underscore, Some(index)) <- allUnderscores(copyOfExpr) zip underscoreIndices
                name = underscoreName(index)
                referenceExpression = ScalaPsiElementFactory.createReferenceExpressionFromText(name)
              } {
                underscore.replaceExpression(referenceExpression, removeParenthesis = false)
              }
              copyOfExpr
          }
      }
    }
    def toTextWithNormalizedUnderscores(expr: ScExpression): String = {
      normalizeUnderscores(expr).getText
    }

    def hasMethod(ty: ScType, methodName: String): Boolean = {
      val processor = new CompletionProcessor(StdKinds.methodRef, this, isImplicit = true) {
        var found = false
        override protected def execute(namedElement: PsiNamedElement)
                                      (implicit state: ResolveState): Boolean = {
          super.execute(namedElement)
          found = !levelSet.isEmpty
          !found
        }

        override protected val forName = Some(methodName)
      }
      processor.processType(ty, this)
      processor.found
    }

    def needsDeconstruction(pattern: ScPattern): Boolean = {
      pattern match {
        case null => false
        case _: ScWildcardPattern => false
        case _: ScReferencePattern => false
        case _: ScTypedPattern => false
        case _ => true
      }
    }

    def needsParenthesisAsLambdaArgument(pattern: ScPattern): Boolean = {
      pattern match {
        case null => false
        case _: ScWildcardPattern => false
        case _: ScReferencePattern => false
        case _ => true
      }
    }

    def nextEnumerator(gen: PsiElement): PsiElement = {
      gen.getNextSibling match {
        case guard: ScGuard => guard
        case enum: ScEnumerator => enum
        case gen: ScGenerator => gen
        case null => null
        case elem => nextEnumerator(elem)
      }
    }

    val resultText: StringBuilder = new StringBuilder
    val patternMappings = mutable.Map.empty[ScPattern, Int]

    def markPatternHere(pattern: ScPattern): Unit = {
      if (pattern != null && !patternMappings.contains(pattern))
        patternMappings += pattern -> resultText.length
    }

    val firstGen = enumerators.flatMap(_.generators.headOption) getOrElse {
      return None
    }

    val enums = {
      lazy val enumStream: Stream[PsiElement] = firstGen #:: enumStream.map(nextEnumerator)
      enumStream takeWhile { _ != null }
    }

    def appendFunc(funcName: String, args: Seq[(ScPattern, String)])
                  (appendBody: => Unit): Unit = {
      val argPatterns = args.map(_._1)
      val needsCase = !forDisplay || args.size > 1  || argPatterns.exists(needsDeconstruction)
      val needsParenthesis = args.size > 1 || !needsCase && argPatterns.exists(needsParenthesisAsLambdaArgument)

      resultText ++= "."
      resultText ++= funcName

      resultText ++= (if (needsCase) " { case " else "(")

      if (needsParenthesis)
        resultText ++= "("
      if (args.isEmpty) {
        resultText ++= "_"
      }
      for (((p, text), idx) <- args.zipWithIndex) {
        if (idx != 0) {
          resultText ++= ", "
        }
        markPatternHere(p)
        resultText ++= text
      }
      if (needsParenthesis)
        resultText ++= ")"
      resultText ++= " "
      resultText ++= `=>`
      resultText ++= " "

      // append the body part
      appendBody

      resultText ++= (if (needsCase) " }" else ")")
    }


    def appendGen(gen: ScGenerator, restEnums: Seq[PsiElement], incomingArgs: Seq[(ScPattern, String)]): Unit = {
      val rvalue = Option(gen.rvalue).map(normalizeUnderscores)
      val rvalueType = rvalue map { _.`type`().getOrAny }
      val isLastGen = !restEnums.exists(_.isInstanceOf[ScGenerator])

      val pattern = gen.pattern
      var args = (pattern, pattern.getText) +: incomingArgs

      // start with the generator expression
      val generatorNeedsParenthesis = rvalue.exists {
        rvalue =>
          val inParenthesis = code"($rvalue).foo".getFirstChild.asInstanceOf[ScParenthesisedExpr]
          ScalaPsiUtil.needParentheses(inParenthesis, inParenthesis.innerElement.get)
      }
      if (generatorNeedsParenthesis)
        resultText ++= "("
      resultText ++= rvalue.map(_.getText).getOrElse("???")
      if (generatorNeedsParenthesis)
        resultText ++= ")"

      // add guards and assignment enumerators
      val hasWithFilter = rvalue.exists(rvalue => {
        val rvalueTy = rvalue.`type`().getOrAny
        // try to use withFilter
        // if the type does not have a withFilter method use filter
        // in the case that we want to desugar for the user,
        // check if the type has a filter function,
        // Because if it doesn't have neither, use withFilter as fallback
        hasMethod(rvalueTy, "withFilter") ||
          (forDisplay && !hasMethod(rvalueTy, "filter"))
      })
      val filterFunc = if (hasWithFilter) "withFilter" else "filter"

      val (nextNonGens, nextEnums) = restEnums span { !_.isInstanceOf[ScGenerator] }

      lazy val curGenName = s"g$${newNameIdx()}"

      nextNonGens foreach {
        case guard: ScGuard =>
          appendFunc(filterFunc, args) {
            resultText ++= guard.expr.map(toTextWithNormalizedUnderscores).getOrElse("???")
            if (!forDisplay) {
              // make sure the result type is Boolean to support type checking
              resultText ++= "; true"
            }
          }

        case assign: ScEnumerator =>
          appendFunc("map", args) {
            // remove wildcard args
            args = args.filterNot(_._2 == "_")

            if (args.nonEmpty)
              resultText ++= "("
            resultText ++= (args.map(_._2) :+ Option(assign.rvalue).map(toTextWithNormalizedUnderscores).getOrElse("???")).mkString(", ")
            if (args.nonEmpty)
              resultText ++= ")"

            args :+= Option(assign.pattern).map(p => p -> p.getText).getOrElse((null, s"v$${newNameIdx()}"))
          }
      }

      // todo: fully implement pattern.isIrrefutableFor to handle pattern matching correctly (use collect)
      // for now, just assume that pattern matching is not needed, so we don't generate so many case clauses
      /*val patternMatchMightFail = false//!forDisplay || !pattern.isIrrefutableFor(pattern.expectedType)
      val (funcText, needsWildcardCase) = if (isLastGen) {
        if (isYield)
          (if (patternMatchMightFail) "collect" else "map", false)
        else
          ("foreach", patternMatchMightFail)
      } else {
        ("flatMap", patternMatchMightFail)
      }*/

      val funcText = if (isLastGen)
        if (isYield) "map" else "foreach"
      else
        "flatMap"

      appendFunc(funcText, args) {
        nextEnums.headOption match {
          case Some(nextGen: ScGenerator) =>
            appendGen(nextGen, nextEnums.tail, List.empty)
          case _ =>
            assert(nextEnums.isEmpty)

            // sometimes the body of a for loop is enclosed in {}
            // we can remove these brackets
            def withoutBodyBrackets(e: ScExpression): ScExpression = e match {
              case ScBlockExpr.Expressions(inner) => inner
              case _ => e
            }

            resultText ++= body.map(normalizeUnderscores).map(withoutBodyBrackets).map(bodyToText).getOrElse("{}")
        }
      }
    }


    appendGen(firstGen, enums.tail, List.empty)
    val desugarizedExprText = resultText.toString
    if (underscores.nonEmpty) {
      val lambdaPrefix = underscores.values.map(underscoreName) match {
        case Seq(arg) => arg + " " + `=>` + " "
        case args => args.mkString("(", ", ", ") ") + `=>` + " "
      }

      Some(lambdaPrefix + desugarizedExprText -> patternMappings.toSeq.map { case (p, idx) => (p, idx + lambdaPrefix.length) })
    } else {
      Some(desugarizedExprText, patternMappings.toSeq)
    }
  }

  override protected def innerType: TypeResult = {
    getDesugarizedExpr flatMap {
      case f: ScFunctionExpr => f.result
      case e => Some(e)
    } match {
      case Some(newExpr) => newExpr.getNonValueType()
      case None => Failure("Cannot create expression")
    }
  }

  def getLeftParenthesis = Option(findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS))

  def getRightParenthesis = Option(findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS))

  override def toString: String = "ForStatement"
}
