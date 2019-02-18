package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScForImpl._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

import scala.annotation.tailrec
import scala.collection.mutable
/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScForImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScFor {
  def isYield: Boolean = findChildByType[PsiElement](ScalaTokenTypes.kYIELD) != null

  def enumerators: Option[ScEnumerators] = findChild(classOf[ScEnumerators])

  // Binding patterns in reverse order
  def patterns: Seq[ScPattern] = enumerators.toSeq.flatMap(_.patterns)

  override def desugared(forDisplay: Boolean): Option[ScExpression] = {
    val result =
      if (forDisplay) generateDesugaredExprWithMappings(forDisplay = true)
      else getDesugaredExprWithMappings

    result map { case (expr, _, _) => expr }
  }


  def desugarPattern(pattern: ScPattern): Option[ScPattern] = {
    getDesugaredExprWithMappings flatMap {
      case (_, patternMapping, _) =>
        patternMapping.get(pattern)
    }
  }

  def desugarEnumerator(enumerator: ScEnumerator): Option[ScEnumerator.DesugaredEnumerator] = {
    getDesugaredExprWithMappings flatMap {
      case (_, _, enumMapping) =>
        enumMapping.get(enumerator)
    }
  }


  override protected def innerType: TypeResult = {
    desugared() flatMap {
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




  // we only really need to cache the version that is used by type inference
  @Cached(ModCount.getBlockModificationCount, this)
  private def getDesugaredExprWithMappings: Option[(ScExpression, Map[ScPattern, ScPattern], Map[ScEnumerator, ScEnumerator.DesugaredEnumerator])] =
    generateDesugaredExprWithMappings(forDisplay = false)

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

  protected def bodyToText(expr: ScalaPsiElement): String = expr.getText

  private def generateDesugaredExprWithMappings(forDisplay: Boolean): Option[(ScExpression, Map[ScPattern, ScPattern], Map[ScEnumerator, ScEnumerator.DesugaredEnumerator])] = {
    generateDesugaredExprTextWithMappings(forDisplay) flatMap {
      case (desugaredText, patternToPosition, enumToPosition) =>
        ScalaPsiElementFactory.createOptionExpressionWithContextFromText(desugaredText, this.getContext, this) map {
          expr =>
            lazy val patternMapping = {
              for {
                (originalPattern, idx) <- patternToPosition
                elem <- Option(expr.findElementAt(idx))
                mc <- findPatternElement(elem, originalPattern)
              } yield originalPattern -> mc
            }.toMap

            lazy val enumMapping = {
              for {
                (enum, idx) <- enumToPosition
                elem <- Option(expr.findElementAt(idx))
                mc <- elem.parentOfType(classOf[ScMethodCall])
              } yield enum -> new ScEnumeratorImpl.DesugaredEnumeratorImpl(mc, enum)
            }.toMap

            if (forDisplay) (expr, Map.empty, Map.empty)
            else (expr, patternMapping, enumMapping)
        }
    }
  }

  private def generateDesugaredExprTextWithMappings(forDisplay: Boolean): Option[(String, TraversableOnce[(ScPattern, Int)], TraversableOnce[(ScEnumerator, Int)])] = {
    val forceSingleLine = !forDisplay || !this.getText.contains("\n")

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

    def needsPatternMatchFilter(pattern: ScPattern): Boolean =
      !pattern.isIrrefutableFor(if (forDisplay) pattern.expectedType else None)

    val resultText: StringBuilder = new StringBuilder
    val patternMappings = mutable.Map.empty[ScPattern, Int]
    val enumMappings = mutable.Map.empty[ScEnumerator, Int]

    def markMappingHere[K](whatOpt: Option[K], mappings: mutable.Map[K, Int]): Unit = {
      for (what <- whatOpt if !mappings.contains(what))
        mappings += what -> resultText.length
    }

    def appendFunc[R](funcName: String, enum: Option[ScEnumerator], args: Seq[(Option[ScPattern], String)], forceCases: Boolean = false, forceBlock: Boolean = false)
                  (appendBody: => R): R = {
      val argPatterns = args.flatMap(_._1)
      val needsCase = !forDisplay || forceCases || args.size > 1  || argPatterns.exists(needsDeconstruction)
      val needsParenthesis = args.size > 1 || !needsCase && argPatterns.exists(needsParenthesisAsLambdaArgument)

      if (!forceSingleLine) {
        resultText ++= "\n"
      }
      resultText ++= "."
      markMappingHere(enum, enumMappings)
      resultText ++= funcName

      resultText ++= (if (needsCase) " { case " else if (forceBlock) " { " else "(")

      if (needsParenthesis)
        resultText ++= "("
      if (args.isEmpty) {
        resultText ++= "_"
      }
      for (((p, text), idx) <- args.zipWithIndex) {
        if (idx != 0) {
          resultText ++= ", "
        }
        markMappingHere(p, patternMappings)
        resultText ++= text
      }
      if (needsParenthesis)
        resultText ++= ")"
      resultText ++= " "
      resultText ++= `=>`
      resultText ++= " "

      // append the body part
      val ret = appendBody

      resultText ++= (if (needsCase || forceBlock) " }" else ")")
      ret
    }

    def appendGen(gen: ScGenerator, restEnums: Seq[ScEnumerator]): Unit = {
      val rvalue = gen.expr.map(normalizeUnderscores)
      val isLastGen = !restEnums.exists(_.isInstanceOf[ScGenerator])

      val pattern = gen.pattern
      type Arg = (Option[ScPattern], String)
      val initialArg = Seq(Some(pattern) -> pattern.getText)

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
      val filterFunc = {
        lazy val rvalueType = rvalue.flatMap(_.`type`().toOption)
        def hasWithFilter = rvalueType.exists(hasMethod(_, "withFilter"))
        def hasFilter = rvalueType.exists(hasMethod(_, "filter"))

        // try to use withFilter
        // if the type does not have a withFilter method use filter except if filter doesn't exist either
        // since 2.12 the compiler doesn't rewrite withFilter to filter, so don't fall back at all!
        val compilerFallsBackToFilter = this.scalaLanguageLevel.exists(_ < ScalaLanguageLevel.Scala_2_12)
        if (!compilerFallsBackToFilter || hasWithFilter || !hasFilter) "withFilter"
        else "filter"
      }

      if (!this.betterMonadicForEnabled && needsPatternMatchFilter(pattern)) {
        appendFunc(filterFunc, None, initialArg, forceCases = true) {
          resultText ++= "true; case _ => false"
        }
      }

      case class ForBinding(forBinding: ScForBinding) {
        def expr: Option[ScExpression] = forBinding.expr

        def pattern: Option[ScPattern] = Option(forBinding.pattern)

        def isWildCard: Boolean = pattern.exists(_.isInstanceOf[ScWildcardPattern])

        val ownName: Option[String] = pattern collect {
          case p: ScNamingPattern => p.name
          case p: ScReferencePattern => p.name
          case p: ScTypedPattern => p.name
        }

        val name: String = ownName.getOrElse("v$" + newNameIdx())

        def patternText: String =
          pattern map { pattern => pattern.getText } getOrElse name

        def exprText: String =
          expr map {
            toTextWithNormalizedUnderscores
          } getOrElse "???"
      }

      def printForBindings(forBindings: Seq[ForBinding], newLines: Boolean): Unit = {
        if (newLines) {
          resultText ++= "\n"
        }
        forBindings foreach {
          binding =>
            val pattern = binding.pattern
            val patternText = binding.patternText

            resultText ++= "val "
            if (binding.ownName.isDefined || (forDisplay && binding.isWildCard)) {
              markMappingHere(pattern, patternMappings)
              resultText ++= patternText
            } else {
              val needsParenthesis = forDisplay && pattern.exists(needsParenthesisAsNamedPattern)
              resultText ++= binding.name
              resultText ++= "@"
              if (needsParenthesis)
                resultText ++= "("
              markMappingHere(pattern, patternMappings)
              resultText ++= patternText
              if (needsParenthesis)
                resultText ++= ")"
            }
            resultText ++= " = "
            resultText ++= binding.exprText
            resultText ++= (if (newLines) "\n" else "; ")
        }
      }

      // before a guard can be printed, all forBindings have to be mapped into the argument tuple
      def printForBindingMap(forBindings: Seq[ForBinding], args: Seq[Arg]): Seq[Arg] = {
        forBindings match {
          case first +: _ =>
            appendFunc("map", Some(first.forBinding), args, forceBlock = true) {
              val multilineForBindings = !forceSingleLine && (forBindings.length > 1 || forBindings.exists(_.forBinding.getText.contains("\n")))
              printForBindings(forBindings, newLines = multilineForBindings)

              if (multilineForBindings) {
                resultText ++= "\n"
              }

              // remove wildcards
              val argsWithoutWildcards = args.filterNot(_._2 == "_")

              val usedBindings = if (forDisplay) forBindings.filter(!_.isWildCard) else forBindings
              val needsArgParenthesis = argsWithoutWildcards.length + usedBindings.size != 1

              // if args is empty we return ()
              if (needsArgParenthesis)
                resultText ++= "("
              resultText ++= (argsWithoutWildcards.map(_._2) ++ usedBindings.map(_.name)).mkString(", ")
              if (needsArgParenthesis)
                resultText ++= ")"

              if (multilineForBindings) {
                resultText ++= "\n"
              }

              argsWithoutWildcards ++ usedBindings.map(b => b.pattern -> b.patternText)
            }
          case _ =>
            args
        }
      }

      val (forBindingsAndGuards, nextEnums) = restEnums span { !_.isInstanceOf[ScGenerator] }

      val (forBindingsInGenBody, generatorArgs) = {
        // accumulate all ForBindings for the next guard or
        forBindingsAndGuards.foldLeft[(Seq[ForBinding], Seq[Arg])]((Seq.empty, initialArg)) {
          case ((forBindings, args), forBinding: ScForBinding) =>
            (forBindings :+ ForBinding(forBinding), args)

          case ((forBindings, args), guard: ScGuard) =>
            val argsWithBindings = printForBindingMap(forBindings, args)

            appendFunc(filterFunc, Some(guard), argsWithBindings) {
              resultText ++= guard.expr.map(toTextWithNormalizedUnderscores).getOrElse("???")
            }

            (Seq.empty, argsWithBindings)
        }
      }

      val funcText = if (isYield)
        if (isLastGen) "map" else "flatMap"
      else
        "foreach"

      val needsMultiline = !forceSingleLine && forBindingsInGenBody.nonEmpty
      appendFunc(funcText, Some(gen), generatorArgs, forceBlock = needsMultiline || forBindingsInGenBody.nonEmpty) {
        printForBindings(forBindingsInGenBody, newLines = needsMultiline)

        nextEnums.headOption match {
          case Some(nextGen: ScGenerator) =>
            if (!forceSingleLine) {
              resultText ++= "\n"
            }
            appendGen(nextGen, nextEnums.tail)

            if (!forceSingleLine) {
              resultText ++= "\n"
            }
          case _ =>
            assert(nextEnums.isEmpty)

            if (needsMultiline) {
              // add an empty line between the value definitions of the for-bindings and the body
              // to avoid merging
              resultText ++= "\n"
            }

            // sometimes the body of a for loop is enclosed in {}
            // we can remove these brackets
            def withoutBodyBrackets(e: ScExpression): ScalaPsiElement = e match {
              case ScBlockExpr.Statements(inner) => inner
              case _ => e
            }

            resultText ++= body
              .map(normalizeUnderscores)
              .map(withoutBodyBrackets)
              .map(bodyToText)
              .getOrElse("{}")

            if (needsMultiline) {
              resultText ++= "\n"
            }
        }
      }
    }

    val firstGen = enumerators.flatMap(_.generators.headOption) getOrElse {
      return None
    }

    val enums = firstGen.withNextSiblings.collect { case e: ScEnumerator => e }.toList

    appendGen(firstGen, enums.tail)

    val desugaredExprText = resultText.toString
    Some(if (underscores.nonEmpty) {
      val lambdaPrefix = underscores.values.map(underscoreName) match {
        case Seq(arg) => arg + " " + `=>` + " "
        case args => args.mkString("(", ", ", ") ") + `=>` + " "
      }

      def adjustPosition[T](pair: (T, Int)): (T, Int) = {
        val (o, idx) = pair
        (o, idx + lambdaPrefix.length)
      }

      (lambdaPrefix + desugaredExprText,
        patternMappings.iterator.map(adjustPosition),
        enumMappings.iterator.map(adjustPosition))
    } else {
      (desugaredExprText,
        patternMappings.iterator,
        enumMappings.iterator)
    })
  }

  private def hasMethod(ty: ScType, methodName: String): Boolean = {
    var found = false
    val processor: CompletionProcessor= new CompletionProcessor(StdKinds.methodRef, this, isImplicit = true) {
      override protected def execute(namedElement: PsiNamedElement)
                                    (implicit state: ResolveState): Boolean = {
        super.execute(namedElement)
        found = !levelSet.isEmpty
        !found
      }

      override protected val forName = Some(methodName)
    }
    processor.processType(ty, this)
    found
  }
}

object ScForImpl {
  private def needsDeconstruction(pattern: ScPattern): Boolean = {
    pattern match {
      case null => false
      case _: ScWildcardPattern => false
      case _: ScReferencePattern => false
      case _: ScTypedPattern => false
      case _ => true
    }
  }

  private def needsParenthesisAsLambdaArgument(pattern: ScPattern): Boolean = {
    pattern match {
      case _: ScWildcardPattern => false
      case _: ScReferencePattern => false
      case _ => true
    }
  }

  private def needsParenthesisAsNamedPattern(pattern: ScPattern): Boolean = {
    pattern match {
      case _: ScWildcardPattern => false
      case _: ScReferencePattern => false
      case _: ScTuplePattern => false
      case _: ScConstructorPattern => false
      case _: ScParenthesisedPattern => false
      case _: ScStableReferencePattern => false
      case _ => true
    }
  }
  @tailrec
  def findPatternElement(e: PsiElement, org: ScPattern): Option[ScPattern] = {
    e match {
      case pattern: ScPattern if pattern.getTextLength == org.getTextLength =>
        Some(pattern)
      case _ if e.getTextLength <= org.getTextLength =>
        findPatternElement(e.getParent, org)
      case _ =>
        None
    }
  }
}