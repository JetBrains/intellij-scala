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

import org.scalatest.FeatureSpec

class FeatureSpecFinderSuite extends FinderSuite {
  
  test("FeatureSpecFinder should find test name for tests written in test suite that extends org.scalatest.FeatureSpec") {
    class TestingFeatureSpec extends FeatureSpec {
      feature("feature 1") {
        scenario("scenario 1") {
          
        }
        scenario("scenario 2") {
          scenario("nested scenario") {
            
          }
        }
      }
      feature("feature 2") {
        println("nested feature 2")
        scenario("scenario 1") {
          
        }
        scenario("scenario 2") {
          
        }
      }
      scenario("scenario with no scope") {
        
      }
    }
    
    val suiteClass = classOf[TestingFeatureSpec]
    val featureSpecClassDef = new ClassDefinition(suiteClass.getName, null, Array.empty, "TestingFeatureSpec")
    val featureSpecConstructor = new ConstructorBlock(suiteClass.getName, featureSpecClassDef, Array.empty)
    val feature1 = new MethodInvocation(suiteClass.getName, null, featureSpecConstructor, Array(), "feature", new StringLiteral(suiteClass.getName, null, "feature 1"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val feature1Scenario1 = new MethodInvocation(suiteClass.getName, null, feature1, Array.empty, "scenario", new StringLiteral(suiteClass.getName, null, "scenario 1"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val feature1Scenario2 = new MethodInvocation(suiteClass.getName, null, feature1, Array.empty, "scenario", new StringLiteral(suiteClass.getName, null, "scenario 2"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val nestedScenario = new MethodInvocation(suiteClass.getName, null, feature1Scenario2, Array.empty, "scenario", new StringLiteral(suiteClass.getName, null, "nested scenario"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    val feature2 = new MethodInvocation(suiteClass.getName, null, featureSpecConstructor, Array.empty, "feature", new StringLiteral(suiteClass.getName, null, "feature 2"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val nestedFeature2 = new MethodInvocation(suiteClass.getName, null, feature2, Array.empty, "println", new StringLiteral(suiteClass.getName, null, "nested feature 2"))
    val feature2Scenario1 = new MethodInvocation(suiteClass.getName, null, feature2, Array.empty, "scenario", new StringLiteral(suiteClass.getName, null, "scenario 1"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    val feature2Scenario2 = new MethodInvocation(suiteClass.getName, null, feature2, Array.empty, "scenario", new StringLiteral(suiteClass.getName, null, "scenario 2"), new ToStringTarget(suiteClass.getName, null, Array.empty, "{}"))
    
    val noScopeScenario = new MethodInvocation(suiteClass.getName, null, featureSpecConstructor, Array.empty, "scenario", new StringLiteral(suiteClass.getName, null, "scenario with no scope"))
    
    val finders = LocationUtils.getFinders(suiteClass)
    assert(finders.size == 1, "org.scalatest.FeatureSpec should have 1 finder, but we got: " + finders.size)
    val finder = finders.get(0)
    assert(finder.getClass == classOf[FeatureSpecFinder], "Suite that uses org.scalatest.FeatureSpec should use FeatureSpecFinder.")
    
    val f1s1 = finder.find(feature1Scenario1)                      
    expectSelection(f1s1, suiteClass.getName, "Feature: feature 1 Scenario: scenario 1", Array("Feature: feature 1 Scenario: scenario 1"))
    val f1s2 = finder.find(feature1Scenario2)
    expectSelection(f1s2, suiteClass.getName, "Feature: feature 1 Scenario: scenario 2", Array("Feature: feature 1 Scenario: scenario 2"))
    val nsf1s2 = finder.find(nestedScenario)
    expectSelection(nsf1s2, suiteClass.getName, "Feature: feature 1 Scenario: scenario 2", Array("Feature: feature 1 Scenario: scenario 2"))
    
    val f2s1 = finder.find(feature2Scenario1)
    expectSelection(f2s1, suiteClass.getName, "Feature: feature 2 Scenario: scenario 1", Array("Feature: feature 2 Scenario: scenario 1"))
    val f2s2 = finder.find(feature2Scenario2)
    expectSelection(f2s2, suiteClass.getName, "Feature: feature 2 Scenario: scenario 2", Array("Feature: feature 2 Scenario: scenario 2"))
    
    val f1 = finder.find(feature1)
    expectSelection(f1, suiteClass.getName, "Feature: feature 1", Array("Feature: feature 1 Scenario: scenario 1", "Feature: feature 1 Scenario: scenario 2"))
    
    val f2 = finder.find(feature2)
    expectSelection(f2, suiteClass.getName, "Feature: feature 2", Array("Feature: feature 2 Scenario: scenario 1", "Feature: feature 2 Scenario: scenario 2"))
    
    val nsf2 = finder.find(nestedFeature2)
    expectSelection(nsf2, suiteClass.getName, "Feature: feature 2", Array("Feature: feature 2 Scenario: scenario 1", "Feature: feature 2 Scenario: scenario 2"))
    
    val nscope = finder.find(noScopeScenario)
    expectSelection(nscope, suiteClass.getName, "Scenario: scenario with no scope", Array("Scenario: scenario with no scope"))
  }

}