package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.{Model, ObjectExt, PsiElementExt, StringsExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScForImpl._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

import scala.annotation.tailrec
import scala.collection.mutable

class ScForImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScFor {
  override def isYield: Boolean = yieldKeyword != null

  override def yieldOrDoKeyword: Option[PsiElement] =
    Option(findChildByType[PsiElement](ScalaTokenTypes.YIELD_OR_DO))

  @inline
  private def yieldKeyword: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.kYIELD)

  override def enumerators: Option[ScEnumerators] = findChild[ScEnumerators]

  // Binding patterns in reverse order
  override def patterns: Seq[ScPattern] = enumerators.toSeq.flatMap(_.patterns)

  override def desugared(forDisplay: Boolean): Option[ScExpression] = {
    val result =
      if (forDisplay) generateDesugaredExprWithMappings(forDisplay = true)
      else getDesugaredExprWithMappings

    result map { case (expr, _, _) => expr }
  }

  override def desugarPattern(pattern: ScPattern): Option[ScPattern] = {
    getDesugaredExprWithMappings flatMap {
      case (_, patternMapping, _) =>
        patternMapping.get(pattern)
    }
  }

  override def desugarEnumerator(enumerator: ScEnumerator): Option[ScEnumerator.DesugaredEnumerator] = {
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
      case None => Failure(ScalaBundle.message("cannot.create.expression"))
    }
  }

  override def getLeftParenthesis: Option[PsiElement] = Option(findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS))

  override def getRightParenthesis: Option[PsiElement] = Option(findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS))

  override def getLeftBracket: Option[PsiElement] = Option(findChildByType[PsiElement](ScalaTokenTypes.LEFT_BRACE_OR_PAREN_TOKEN_SET))

  override def getRightBracket: Option[PsiElement] = Option(findChildByType[PsiElement](ScalaTokenTypes.RIGHT_BRACE_OR_PAREN_TOKEN_SET))

  override def toString: String = "ForStatement"

  private def compilerRewritesWithFilterToFilter: Boolean = this.scalaLanguageLevel.exists(_ < ScalaLanguageLevel.Scala_2_12)

  // we only really need to cache the version that is used by type inference
  @Cached(BlockModificationTracker(this), this)
  private def getDesugaredExprWithMappings: Option[(ScExpression, Map[ScPattern, ScPattern], Map[ScEnumerator, ScEnumerator.DesugaredEnumerator])] = {
    visitWithFilterExprs(this)(e => e.putUserData(explicitWithFilterKey, ()))

    generateDesugaredExprWithMappings(forDisplay = false).map {
      case result @ (expr, _, _) =>
        if (compilerRewritesWithFilterToFilter) {
          // annotate withFilter-calls with userdata to control method resolving
          // in ReferenceExpressionResolver
          visitWithFilterExprs(expr) { e =>
            if (!explicitWithFilterKey.isIn(e))
              e.putUserData(desugaredWithFilterKey, ())
          }
        }
        result
    }
  }



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

  private def generateDesugaredExprWithMappings(forDisplay: Boolean) =
    generateDesugaredExprTextWithMappings(forDisplay).map {
      case (expression, patternToPosition, enumToPosition) =>
        val patternMapping = for {
          (original, element) <- patternToPosition
          pattern <- findPatternElement(element, original)
        } yield original -> pattern

        val enumMapping = for {
          (original, element) <- enumToPosition
          methodCall <- element.parentOfType(classOf[ScMethodCall])
        } yield original -> new ScEnumeratorImpl.DesugaredEnumeratorImpl(methodCall, original)

        (expression, patternMapping.toMap, enumMapping.toMap)
    }

  private def generateDesugaredExprTextWithMappings(forDisplay: Boolean) = {
    val forceSingleLine = !(forDisplay && this.getText.contains("\n"))

    var nextNameIdx = 0
    val `=>` = ScalaPsiUtil.functionArrow(getProject)

    val underscores = ScUnderScoreSectionUtil.underscores(this).zipWithIndex.toMap

    def underscoreName(i: Int): String = s"forAnonParam$$$i"

    def allUnderscores(expr: PsiElement): Seq[ScUnderscoreSection] = {
      expr match {
        case underscore: ScUnderscoreSection => Seq(underscore)
        case _ => expr.getChildren.flatMap(allUnderscores).toSeq
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

    val resultText = new mutable.StringBuilder()
    val patternMappings = mutable.Map.empty[ScPattern, Int]
    val enumMappings = mutable.Map.empty[ScEnumerator, Int]

    def markMappingHere[K](whatOpt: Option[K], mappings: mutable.Map[K, Int]): Unit = {
      for (what <- whatOpt if !mappings.contains(what))
        mappings += what -> resultText.length
    }

    def appendFunc[R](
      funcName:   String,
      enum:       Option[ScEnumerator],
      args:       Seq[(Option[ScPattern], String)],
      forceCases: Boolean = false,
      forceBlock: Boolean = false
    )(appendBody: =>R
    ): R = {
      val argPatterns = args.flatMap(_._1)
      val needsCase = !forDisplay || forceCases || args.size > 1 || argPatterns.exists(needsDeconstruction)
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
      val isLastGen = !restEnums.exists(_.is[ScGenerator])

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
      val filterFunc: String = if (forDisplay && compilerRewritesWithFilterToFilter) {
        val rvalueType = rvalue.flatMap(_.`type`().toOption)
        def hasWithFilter = rvalueType.exists(hasMethod(_, "withFilter"))
        def hasFilter = rvalueType.exists(hasMethod(_, "filter"))

        // try to use withFilter
        // if the type does not have a withFilter method use filter except if filter doesn't exist either
        if (hasWithFilter || !hasFilter) "withFilter"
        else "filter"
      } else {
        "withFilter"
      }

      if (!this.betterMonadicForEnabled && needsPatternMatchFilter(pattern)) {
        appendFunc(filterFunc, None, initialArg, forceCases = true) {
          resultText ++= "true; case _ => false"
        }
      }

      case class ForBinding(forBinding: ScForBinding) {

        def exprText: String =
          forBinding.expr
            .fold("???")(toTextWithNormalizedUnderscores)

        val pattern: Option[ScPattern] = Option(forBinding.pattern)

        private val bindingPattern: Option[ScBindingPattern] = pattern.collect {
          case pattern: ScBindingPattern => pattern
        }

        val isBinding: Boolean = bindingPattern.isDefined
        val isWildCard: Boolean = pattern.exists(_.is[ScWildcardPattern])

        val name: String = bindingPattern.fold({
          nextNameIdx += 1
          s"v$$${if (forDisplay) "" else "forIntellij"}$nextNameIdx"
        })(_.name)

        def patternText: String = pattern.fold(name)(_.getText)
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
            if (binding.isBinding || (forDisplay && binding.isWildCard)) {
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

      val (forBindingsAndGuards, nextEnums) = restEnums.span(!_.is[ScGenerator])

      val (forBindingsInGenBody, generatorArgs) = {
        // accumulate all ForBindings for the next guard
        // (but not if we want to display it, because it changes semantic)
        // see #SCL-16463
        forBindingsAndGuards.foldLeft[(Seq[ForBinding], Seq[Arg])]((Seq.empty, initialArg)) {
          case ((forBindings, args), forBinding: ScForBinding) =>
            if (!forDisplay) (forBindings :+ ForBinding(forBinding), args)
            else {
              val argsWithBindings = printForBindingMap(Seq(ForBinding(forBinding)), args)
              (Seq.empty, argsWithBindings)
            }

          case ((forBindings, args), guard: ScGuard) =>
            val argsWithBindings = printForBindingMap(forBindings, args)

            appendFunc(filterFunc, Some(guard), argsWithBindings) {
              resultText ++= guard.expr.map(toTextWithNormalizedUnderscores).getOrElse("???")
            }

            (Seq.empty, argsWithBindings)
          case _ =>
            ???
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

    enumerators.flatMap(_.generators.headOption).map { firstGen =>
      val restEnums = firstGen.withNextSiblings
        .collect {
          case e: ScEnumerator => e
        }.toList
        .tail

      appendGen(firstGen, restEnums)

      val lambdaPrefix = underscores.valuesIterator match {
        case iterator if iterator.isEmpty => ""
        case iterator =>
          (iterator.map(underscoreName).toSeq match {
            case Seq(arg) => arg
            case args => args.commaSeparated(model = Model.Parentheses)
          }) + " " + `=>` + " "
      }

      // we need the braces to handle incomplete fors, because createExpressionWithContextFromText
      // needs to parse exactly one expression.
      // The parenthesis are needed for correct newline-handling
      val prefix = "{("
      resultText.insert(0, prefix + lambdaPrefix)
      resultText ++= ")}"
      val expression = ScalaPsiElementFactory.createExpressionWithContextFromText(
        resultText.toString,
        getContext,
        this
      )
      val shiftOffset = lambdaPrefix.length + prefix.length

      def withElements[T](mappings: mutable.Map[T, Int]) = for {
        (original, offset) <- if (forDisplay) Iterator.empty else mappings.iterator

        element = expression.findElementAt(offset + shiftOffset)
        if element != null
      } yield original -> element

      val desugared = expression match {
        case ScBlock(ScParenthesisedExpr(desugaredFor)) =>
          desugaredFor.context = getContext
          desugaredFor.child = this
          desugaredFor
        case _ =>
          expression
      }

      (desugared, withElements(patternMappings), withElements(enumMappings))
    }
  }

  private def hasMethod(ty: ScType, methodName: String): Boolean = {
    var found = false
    val processor: CompletionProcessor = new CompletionProcessor(StdKinds.methodRef, this, withImplicitConversions = true) {
      override protected def execute(namedElement: PsiNamedElement)
                                    (implicit state: ResolveState): Boolean = {
        super.execute(namedElement)
        found = !levelSet.isEmpty
        !found
      }

      override protected val forName: Some[String] = Some(methodName)
    }
    processor.processType(ty, this)
    found
  }
}

object ScForImpl {
  val desugaredWithFilterKey: Key[Unit]        = new Key("DESUGARED_withFilter_METHOD")
  private val explicitWithFilterKey: Key[Unit] = new Key("explicit_withFilter_in_for_comp")

  private def visitWithFilterExprs(expr: ScExpression)(f: ScReferenceExpression => Unit): Unit =
    expr.accept(new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        if (ref.refName == "withFilter") f(ref)
        super.visitReferenceExpression(ref)
      }
    })


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
  private def findPatternElement(e: PsiElement, org: ScPattern): Option[ScPattern] = {
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