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

package org.scalatest.finders;

import java.util.ArrayList;
import java.util.List;

public class FeatureSpecFinder implements Finder {
  
  @Override
  public Selection find(AstNode node) {
    Selection result = null;
    while (result == null) {
      if (node instanceof MethodInvocation invocation) {
        String name = invocation.name();
        AstNode[] args = invocation.args();
        if (isScenarioRef(name) && args.length > 0 && args[0].canBePartOfTestName()) {
          AstNode parent = invocation.parent();
          if (parent instanceof ConstructorBlock)
            result = new Selection(parent.className(), "Scenario: " + args[0].toString(), new String[] { "Scenario: " + args[0].toString() });
          else if (parent instanceof MethodInvocation parentInvocation) {
            String parentName = parentInvocation.name();
            AstNode[] parentArgs = parentInvocation.args();
            if (isFeatureRef(parentName) && parentArgs.length > 0 && parentArgs[0].canBePartOfTestName()) {
              String testName = "Feature: " + parentArgs[0] + " Scenario: " + args[0];
              result = new Selection(parentInvocation.className(), testName, new String[] { testName });
            }
            else {
              if (node.parent() != null) 
                node = node.parent();
              else
                break;
            }
          }
          else {
            if (node.parent() != null) 
              node = node.parent();
            else
              break;
          }
        }
        else if (isFeatureRef(name) && args.length > 0 && args[0].canBePartOfTestName()) {
          AstNode parent = invocation.parent();
          if (parent instanceof ConstructorBlock) {
            String featureText = "Feature: " + args[0];
            List<String> testNameList = new ArrayList<>();
            AstNode[] children = invocation.children();
            for (AstNode childNode : children) {
              if (childNode instanceof MethodInvocation child
                  && (isScenarioRef(childNode.name()))
                  && ((MethodInvocation) childNode).args().length > 0
                  && ((MethodInvocation) childNode).args()[0] instanceof StringLiteral) {
                if (child.args()[0].canBePartOfTestName()) {
                  testNameList.add(featureText + " Scenario: " + child.args()[0]);
                }
              }
            }
            result = new Selection(invocation.className(), featureText, testNameList.toArray(new String[0]));
          }
          else {
            if (node.parent() != null) 
              node = node.parent();
            else
              break;
          }
        }
        else {
          if (node.parent() != null) 
            node = node.parent();
          else
            break;
        }
      }
      else {
        if (node.parent() != null) 
          node = node.parent();
        else
          break;
      }
    }
    return result;
  }

  private boolean isFeatureRef(String refName) {
    return refName.equals("feature") || refName.equals("Feature");
  }

  private boolean isScenarioRef(String refName) {
    return refName.equals("scenario") || refName.equals("Scenario");
  }
}
