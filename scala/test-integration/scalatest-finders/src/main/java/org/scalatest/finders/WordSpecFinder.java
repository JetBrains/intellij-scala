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

import java.util.*;

import static org.scalatest.finders.utils.StringUtils.isMethod;

public class WordSpecFinder implements Finder {

  private final Set<String> scopeSet = Set.of(
      "should",
      "must",
      "can",
      "which",
      "when",
      "that" // 'that' is deprecated
  );

  @Override
  public Selection find(final AstNode node) {
    Selection result = null;

    AstNode curNode = node;
    while (result == null && curNode != null) {
      if (curNode instanceof MethodInvocation invocation) {

        String name = invocation.name();
        AstNode parent = invocation.parent();
        if (name.equals("in") && parent != null && scopeSet.contains(parent.name())) {
          String testName = getTestNameBottomUp(invocation);
          result = testName == null ? null : new Selection(invocation.className(), testName, new String[]{testName});
        }
        else if (scopeSet.contains(name)) {
          String displayName = getDisplayNameBottomUp(invocation);
          List<String> testNames = getTestNamesTopDown(invocation);
          result = new Selection(invocation.className(), displayName, testNames.toArray(new String[0]));
        }
      }

      curNode = curNode.parent();
    }

    return result;
  }
  
  private String getTestNameBottomUp(final MethodInvocation invocation2) {
    StringBuilder result = new StringBuilder();

    MethodInvocation curInvocation = invocation2;
    while (curInvocation != null) {
      if (curInvocation.name().equals("in")) {
        AstNode target = curInvocation.target().name().equals("taggedAs") && curInvocation.target() instanceof MethodInvocation
            ? ((MethodInvocation) curInvocation.target()).target()
            : curInvocation.target();
        if (!target.canBePartOfTestName()) return null;
        result.insert(0, target + " " );
      } else {
        AstNode target = curInvocation.target();
        if (!target.canBePartOfTestName()) return null;
        result.insert(0, (target + " " + curInvocation.name() + " "));
      }

      AstNode parent = curInvocation.parent();
      curInvocation =  parent instanceof MethodInvocation ? (MethodInvocation) parent : null;
    }

    return result.toString().trim();
  }
  
  private String getDisplayNameBottomUp(MethodInvocation invocation) {
    String targetDisplayName = invocation.target().toString();
    if (invocation.parent() instanceof MethodInvocation) {
      return getTestNameBottomUp((MethodInvocation) invocation.parent()) + " " + targetDisplayName;
    } else {
      return targetDisplayName;
    }
  }
  
  private List<String> getTestNamesTopDown(MethodInvocation invocation) {
    List<String> results = new ArrayList<>();
    List<AstNode> nodes = new ArrayList<>();
    nodes.add(invocation);
        
    while (nodes.size() > 0) {
      AstNode head = nodes.remove(0);
      if (head instanceof MethodInvocation headInvocation) {
        if (isMethod(headInvocation, "in")) {
          String testName = getTestNameBottomUp(headInvocation);
          if (testName != null) {
            results.add(testName);
          }
        }
        else
          nodes.addAll(0, Arrays.asList(headInvocation.children()));
      }
    }
        
    return results;
  }
}