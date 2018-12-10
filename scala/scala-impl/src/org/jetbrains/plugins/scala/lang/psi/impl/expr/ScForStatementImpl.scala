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
          Option(ScalaPsiElementFactory.createExpressionWithContextFromText(desugaredText, this.getContext, this)) map {
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
    def newNameIdx(): Int = {
      _nextNameIdx += 1
      _nextNameIdx
    }
    val `=>` = ScalaPsiUtil.functionArrow(getProject)

    case class PatternText(text: String, needsDeconstruction: Boolean)

    if (ScUnderScoreSectionUtil.underscores(this).nonEmpty) {
      val copyOf = this.copy().asInstanceOf[ScForStatementImpl]
      val underscores = ScUnderScoreSectionUtil.underscores(copyOf)
      val length = underscores.length

      def name(i: Int): String = s"forAnonParam$$$i"

      underscores.zipWithIndex.foreach {
        case (underscore, index) =>
          val referenceExpression = ScalaPsiElementFactory.createReferenceExpressionFromText(name(index))
          underscore.replaceExpression(referenceExpression, removeParenthesis = false)
      }

      return copyOf.generateDesugarizedExprTextWithPatternMapping(forDisplay) map {
        case (desugarizedExprText, patternMapping) =>

          val lambdaPrefix = (0 until length).map(name).mkString("(", ", ", ") " + `=>` + " ")

          (lambdaPrefix + desugarizedExprText,
            patternMapping.map { case (p, idx) => (p, idx + lambdaPrefix.length) })
      }
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
        case _: ScWildcardPattern => false
        case _: ScReferencePattern => false
        case _: ScTypedPattern => false
        case _ => true
      }
    }

    def needsParenthesisAsLambdaArgument(pattern: ScPattern): Boolean = {
      pattern match {
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
    val patternMappings = mutable.Buffer.empty[(ScPattern, Int)]
    var deconstruct = false

    def markPatternHere(pattern: ScPattern): Unit = {
      patternMappings += pattern -> resultText.length
    }

    val firstGen = enumerators.flatMap(_.generators.headOption) getOrElse {
      return None
    }

    val enums = {
      lazy val enumStream: Stream[PsiElement] = firstGen #:: enumStream.map(nextEnumerator)
      enumStream takeWhile { _ != null }
    }

    def appendFunc(funcName: String, args: Seq[String], newPattern: Option[ScPattern] = None, newPatternNeedsPatternMatching: => Boolean = false)
                  (appendBody: => Unit): Unit = {
      val argTupleSize = newPattern.size + args.size
      deconstruct = deconstruct || newPattern.exists(needsDeconstruction)
      val needsCase = !forDisplay || deconstruct || newPatternNeedsPatternMatching
      val needsParenthesis = argTupleSize > 1 || !needsCase && newPattern.exists(needsParenthesisAsLambdaArgument)

      resultText ++= "."
      resultText ++= funcName

      resultText ++= (if (needsCase) " { case " else "(")

      if (argTupleSize == 0)
        resultText ++= "_"
      if (needsParenthesis)
        resultText ++= "("
      newPattern.foreach {
        p =>
          markPatternHere(p)
          resultText ++= p.getText
          if (args.nonEmpty)
            resultText ++= ", "
      }
      resultText ++= args.mkString(", ")
      if (needsParenthesis)
        resultText ++= ")"
      resultText ++= " "
      resultText ++= `=>`
      resultText ++= " "

      // append the body part
      appendBody

      resultText ++= (if (needsCase) " }" else ")")
    }


    def appendGen(gen: ScGenerator, restEnums: Seq[PsiElement], incomingArgs: Seq[String]): Unit = {
      val rvalue = Option(gen.rvalue)
      val rvalueType = rvalue map { _.`type`().getOrAny }
      val isLastGen = !restEnums.exists(_.isInstanceOf[ScGenerator])

      val pattern = gen.pattern
      var args = incomingArgs

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
      val hasWithFilter = rvalue.exists(rvalue => hasMethod(rvalue.`type`().getOrAny, "withFilter"))
      val filterFunc = if (hasWithFilter) "withFilter" else "filter"

      val (nextNonGens, nextEnums) = restEnums span { !_.isInstanceOf[ScGenerator] }

      def nonGenArgs = pattern.getText +: args

      nextNonGens foreach {
        case guard: ScGuard =>
          appendFunc(filterFunc, nonGenArgs) {
            resultText ++= guard.expr.map(_.getText).getOrElse("???")
            if (!forDisplay) {
              // make sure the result type is Boolean to support type checking
              resultText ++= "; true"
            }
          }

        case assign: ScEnumerator =>
          appendFunc("map", nonGenArgs) {
            if (!forDisplay)
              resultText ++= "{ "
            for {
              pattern <- Option(assign.pattern)
              rvalue <- Option(assign.rvalue)
              if !forDisplay
            } {
              // build this value definition to mark the pattern
              // this is important for the patternMapping
              resultText ++= " val "
              markPatternHere(pattern)
              resultText ++= pattern.getText
              resultText ++= " = "
              resultText ++= rvalue.getText
              resultText ++= "; "
            }
            resultText ++= "("
            if (nonGenArgs.nonEmpty) {
              resultText ++= nonGenArgs.mkString(", ")
              resultText ++= ", "
            }
            resultText ++= Option(assign.rvalue).map(_.getText).getOrElse("???")
            resultText ++= ")"
            deconstruct = true
            args :+= Option(assign.pattern).map(_.getText).getOrElse(s"v${newNameIdx()}")
          }
          if (!forDisplay)
            resultText ++= " }"
      }

      // todo: fully implement pattern.isIrrefutableFor to handle pattern matching correctly
      // for now, just assume that pattern matching is not needed, so we don't generate so many case clauses
      val patternMatchMightFail = false//!forDisplay || !pattern.isIrrefutableFor(pattern.expectedType)
      val (funcText, needsWildcardCase) = if (isLastGen) {
        if (isYield)
          (if (patternMatchMightFail) "collect" else "map", false)
        else
          ("foreach", patternMatchMightFail)
      } else {
        ("flatMap", patternMatchMightFail)
      }

      appendFunc(funcText, args, newPattern = Some(pattern), newPatternNeedsPatternMatching = patternMatchMightFail) {
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

            resultText ++= body.map(withoutBodyBrackets).map(bodyToText).getOrElse("{}")
        }

        if (needsWildcardCase) {
          resultText ++= "; case _ "
          resultText ++= `=>`

          if (!isLastGen) {
            // In the case that flatMap needs a wildcard case we have to return
            // an empty collection of the generator's type.
            // TODO: Find the method that represents an empty collection like Seq.empty
            resultText ++= " ???"
          }
        }
      }
    }


    appendGen(firstGen, enums.tail, List.empty)
    Some(resultText.toString, patternMappings)
  }

  override protected def innerType: TypeResult = {
    getDesugarizedExpr match {
      case Some(newExpr) => newExpr.getNonValueType()
      case None => Failure("Cannot create expression")
    }
  }

  def getLeftParenthesis = Option(findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS))

  def getRightParenthesis = Option(findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS))

  override def toString: String = "ForStatement"
}
