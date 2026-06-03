/*
 * Copyright 2001-2008 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalatest.finders

import org.scalatest.freespec.AnyFreeSpec

class FreeSpecFinderSuite extends FinderSuite {
  
  test("FreeSpecFinder should find test name for tests written in test suite that extends org.scalatest.FreeSpec") {
    class TestingFreeSpec extends AnyFreeSpec {
      "A Stack" - {
        "whenever it is empty" - {
          println("nested -")
          "certainly ought to" - {
            "be empty" in {
        
            }
            "complain on peek" in {
              println("in nested")
            }
            "complain on pop" in {
          
            }
          }
        }
        "but when full, by contrast, must" - {
          "be full" in {
            "in nested" in {
              
            }
          }
          "complain on push" in {
          
          }
        }
      }
    }

    val suiteClass = classOf[TestingFreeSpec]
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FreeSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FreeSpecFinder], "Suite that uses org.scalatest.FreeSpec should use FreeSpecFinder.")

    def toStringTarget(s: String): ToStringTarget = new ToStringTarget(suiteClass.getName, null, Array.empty, s)
    def literal(value: String): StringLiteral = new StringLiteral(suiteClass.getName, null, value)
    def braces: ToStringTarget = toStringTarget("{}")
    def invocation(target: AstNode, parent: AstNode, name: String, args: AstNode*): MethodInvocation =
      new MethodInvocation(suiteClass.getName, target, parent, Array.empty, name, args: _*)

    val aStackNode                = invocation(toStringTarget("A Stack"), null, "-", braces)
    val wheneverItIsEmpty         = invocation(toStringTarget("whenever it is empty"), aStackNode, "-", braces)
    val nestedDash                = invocation(toStringTarget("{Predef}"), wheneverItIsEmpty, "println", literal("nested in"))
    val certainlyOughtTo          = invocation(toStringTarget("certainly ought to"), wheneverItIsEmpty, "-", braces)
    val beEmpty                   = invocation(toStringTarget("be empty"), certainlyOughtTo, "in", braces)
    val complainOnPeek            = invocation(toStringTarget("complain on peek"), certainlyOughtTo, "in", braces)
    val inNested                  = invocation(toStringTarget("{Predef}"), complainOnPeek, "println", literal("in nested"))
    val complainOnPop             = invocation(toStringTarget("complain on pop"), certainlyOughtTo, "in", braces)
    val butWhenFullByContrastMust = invocation(toStringTarget("but when full, by contrast, must"), aStackNode, "-", braces)
    val beFull                    = invocation(toStringTarget("be full"), butWhenFullByContrastMust, "in", braces)
    val nestedIn                  = invocation(toStringTarget("in nested"), beFull, "in", braces)
    val complainOnPush            = invocation(toStringTarget("complain on push"), butWhenFullByContrastMust, "in", braces)
    
    val aStackTest = finder.find(aStackNode)
    expectSelection(aStackTest, suiteClass.getName, "A Stack", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop", 
      "A Stack but when full, by contrast, must be full", 
      "A Stack but when full, by contrast, must complain on push"
    ))
    
    val wheneverItIsEmptyTest = finder.find(wheneverItIsEmpty)
    expectSelection(wheneverItIsEmptyTest, suiteClass.getName, "A Stack whenever it is empty", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop"
    ))
    
    val nestedDashTest = finder.find(nestedDash)
    expectSelection(nestedDashTest, suiteClass.getName, "A Stack whenever it is empty", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop"
    ))
    
    val certainlyOughtToTest = finder.find(certainlyOughtTo)
    expectSelection(certainlyOughtToTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to", Array(
      "A Stack whenever it is empty certainly ought to be empty", 
      "A Stack whenever it is empty certainly ought to complain on peek", 
      "A Stack whenever it is empty certainly ought to complain on pop"
    ))
    
    val beEmptyTest = finder.find(beEmpty)
    expectSelection(beEmptyTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to be empty", Array("A Stack whenever it is empty certainly ought to be empty"))
    
    val complainOnPeekTest = finder.find(complainOnPeek)
    expectSelection(complainOnPeekTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to complain on peek", Array("A Stack whenever it is empty certainly ought to complain on peek"))
    
    val inNestedTest = finder.find(inNested)
    expectSelection(inNestedTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to complain on peek", Array("A Stack whenever it is empty certainly ought to complain on peek"))
    
    val complainOnPopTest = finder.find(complainOnPop)
    expectSelection(complainOnPopTest, suiteClass.getName, "A Stack whenever it is empty certainly ought to complain on pop", Array("A Stack whenever it is empty certainly ought to complain on pop"))
    
    val butWhenFullByContrastMustTest = finder.find(butWhenFullByContrastMust)
    expectSelection(butWhenFullByContrastMustTest, suiteClass.getName, "A Stack but when full, by contrast, must", Array(
      "A Stack but when full, by contrast, must be full", 
      "A Stack but when full, by contrast, must complain on push"    
    ))
    
    val beFullTest = finder.find(beFull)
    expectSelection(beFullTest, suiteClass.getName, "A Stack but when full, by contrast, must be full", Array("A Stack but when full, by contrast, must be full"))
    
    val nestedInTest = finder.find(nestedIn)
    expectSelection(nestedInTest, suiteClass.getName, "A Stack but when full, by contrast, must be full", Array("A Stack but when full, by contrast, must be full"))
    
    val complainOnPushTest = finder.find(complainOnPush)
    expectSelection(complainOnPushTest, suiteClass.getName, "A Stack but when full, by contrast, must complain on push", Array("A Stack but when full, by contrast, must complain on push"))
  }

  test("FreeSpecFinder should find test name for tests written in test suite that extends org.scalatest.FreeSpec for non-nested tests") {
    class TestingFreeSpec extends AnyFreeSpec {
      "not nested" in {
      }
    }

    val suiteClass = classOf[TestingFreeSpec]
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FreeSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FreeSpecFinder], "Suite that uses org.scalatest.FreeSpec should use FreeSpecFinder.")

    def toStringTarget(s: String): ToStringTarget = new ToStringTarget(suiteClass.getName, null, Array.empty, s)
    def braces: ToStringTarget = toStringTarget("{}")
    def invocation(target: AstNode, parent: AstNode, name: String, args: AstNode*): MethodInvocation =
      new MethodInvocation(suiteClass.getName, target, parent, Array.empty, name, args: _*)

    val classDef = new ClassDefinition(suiteClass.getName, null, Array.empty, suiteClass.getSimpleName)
    val constructorBlock = new ConstructorBlock(suiteClass.getName, classDef, Array.empty)
    val notNestedNode = invocation(toStringTarget("not nested"), constructorBlock, "in", braces)
    expectSelection(finder.find(notNestedNode), suiteClass.getName, "not nested", Array("not nested"))
  }
  
}