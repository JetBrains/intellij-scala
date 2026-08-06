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

import static org.scalatest.finders.LocationUtils.getParentOfType;
import static org.scalatest.finders.LocationUtils.isValidName;
import static org.scalatest.finders.utils.StringUtils.is;

public class FlatSpecFinder implements Finder {

  @Override
  public Selection find(AstNode node) {
    Selection result = null;

    while (result == null) {
      if (node instanceof MethodInvocation invocation) {
        if (is(invocation.name(), "of", "in", "should", "must")) {
          ConstructorBlock constructor = getParentOfType(node, ConstructorBlock.class);
          if (constructor != null) {
            AstNode scopeNode = getScopeNode(node, constructor.children());
            if (scopeNode != null) {
              String prefix = getPrefix((MethodInvocation) scopeNode);
              result = prefix == null
                  ? null
                  : getNodeTestSelection(node, prefix, constructor.children());
            }
          }
        }
      }

      if (result == null) {
        if (node.parent() != null)
          node = node.parent();
        else
          break;
      }
    }

    return result;
  }

  private String getPrefix(MethodInvocation invocation) {
    String result = null;

    while (result == null) {
      if (invocation.name().equals("of"))
        //result = invocation.target().toString();
        if (invocation.args()[0].canBePartOfTestName()) {
          result = invocation.args()[0].toString();
        } else {
          break;
        }
      else if (invocation.target() instanceof MethodInvocation invocationTarget) {
        if (is(invocationTarget.name(), "should", "must", "taggedAs")) {
          invocation = invocationTarget;
        } else if (invocationTarget.canBePartOfTestName()) {
          result = invocationTarget.toString();
        } else {
          break;
        }
      }
      else if (invocation.target().canBePartOfTestName()) {
        result = invocation.target().toString();
      }
      else {
        break;
      }
    }

    return result;
  }

  private AstNode getScopeNode(AstNode node, AstNode[] constructorChildren) {
    AstNode topLevelNode = null;
    while (node != null && topLevelNode == null) {
      if (node.parent() instanceof ConstructorBlock)
        topLevelNode = node;
      else
        node = node.parent();
    }

    if (topLevelNode != null) {
      if (isScope(topLevelNode))
        return topLevelNode;
      else {
        List<AstNode> beforeTopLevelNodeList = new ArrayList<>();
        for (AstNode child : constructorChildren) {
          if (!child.equals(topLevelNode))
            beforeTopLevelNodeList.add(child);
          else
            break;
        }
        AstNode scopeNode = null;
        for (int i = beforeTopLevelNodeList.size() - 1; i >= 0; i--) {
          AstNode tnode = beforeTopLevelNodeList.get(i);
          if (isScope(tnode)) {
            scopeNode = tnode;
            break;
          }
        }
        if (scopeNode == null) {
          AstNode tNode = beforeTopLevelNodeList.isEmpty() ? topLevelNode : beforeTopLevelNodeList.get(0);
          if (isScope(node, true)) {
            scopeNode = tNode;
          }
        }
        return scopeNode;
      }
    } else
      return null;
  }

  private boolean isScope(AstNode node) {
    return isScope(node, false);
  }

  private boolean isScope(AstNode node, boolean allowIt) {
    if (node instanceof MethodInvocation invocation) {
      return invocation.name().equals("of") ||
          isScopeTagged(invocation, allowIt) ||
          (invocation.name().equals("in") && invocation.target() != null &&
              invocation.target() instanceof MethodInvocation &&
              isScopeTagged((MethodInvocation) invocation.target(), allowIt));
    } else
      return false;
  }

  private boolean isScopeTagged(MethodInvocation invocation, boolean allowIt) {
    return (invocation.name().equals("taggedAs") && invocation.target() instanceof MethodInvocation &&
        isScopeShould((MethodInvocation) invocation.target(), allowIt)) || isScopeShould(invocation, allowIt);
  }

  private boolean isScopeShould(MethodInvocation invocation, boolean allowIt) {
    return is(invocation.name(), "should", "must") &&
        invocation.args().length > 0 &&
        invocation.target() != null &&
        (allowIt || !isHeadKeyword(invocation));
  }

  private boolean isHeadKeyword(MethodInvocation invocation) {
    String name = invocation.target().name();
    return is(name, "it", "they", "ignore");
  }

  private Selection getNodeTestSelection(AstNode node, String prefix, AstNode[] constructorChildren) {
    if (node instanceof ConstructorBlock) {
      List<String> testNames = getTestNamesFromChildren(prefix, Arrays.asList(node.children()));
      return new Selection(node.className(), prefix.length() > 0 ? prefix : node.className(), testNames.toArray(new String[0]));
    } else if (node instanceof MethodInvocation invocation) {
      String name = invocation.name();
      switch (name) {
        case "of":
          List<AstNode> constructorChildrenList = Arrays.asList(constructorChildren);
          int nodeIdx = constructorChildrenList.indexOf(node);
          if (nodeIdx >= 0) {
            List<AstNode> startList = constructorChildrenList.subList(nodeIdx + 1, constructorChildrenList.size());
            List<AstNode> subList = new ArrayList<>();
            for (AstNode snode : startList) {
              if (!isScope(snode))
                subList.add(snode);
              else
                break;
            }
            List<String> testNames = getTestNamesFromChildren(prefix, subList);
            return new Selection(node.className(), prefix, testNames.toArray(new String[0]));
          } else
            return null;
        case "should":
        case "must":
          AstNode parent = invocation.parent();
          if (parent instanceof MethodInvocation && parent.name().equals("in")) {
            String testName = getTestName(prefix, (MethodInvocation) parent);
            return testName != null ? new Selection(invocation.className(), testName, new String[]{testName}) : null;
          } else
            return null;
        case "in":
          String testName = getTestName(prefix, invocation);
          return testName != null ? new Selection(invocation.className(), testName, new String[]{testName}) : null;
        default:
          return null;
      }
    } else
      return null;
  }

  private List<String> getTestNamesFromChildren(String prefix, List<AstNode> children) {
    Set<String> validSet = new HashSet<>();
    validSet.add("in");
    List<String> testNameList = new ArrayList<>();
    for (AstNode node : children) {
      if (node instanceof MethodInvocation invocation && isValidName(node.name(), validSet)) {
        String testName = getTestName(prefix, invocation);
        if (testName != null) {
          testNameList.add(testName);
        }
      }
    }
    return testNameList;
  }

  private String getTargetString(AstNode target, String postfix) {
    if (target == null)
      return postfix;
    else {
      if (target instanceof MethodInvocation && target.name().equals("should") && ((MethodInvocation) target).args()[0].canBePartOfTestName())
        return "should " + ((MethodInvocation) target).args()[0];
      else if (target instanceof MethodInvocation && target.name().equals("must") && ((MethodInvocation) target).args()[0].canBePartOfTestName())
        return "must " + ((MethodInvocation) target).args()[0];
      else if (target instanceof MethodInvocation && target.name().equals("taggedAs")) {
        return getTargetString(((MethodInvocation) target).target(), postfix);
      } else if (target.canBePartOfTestName())
        return target.toString();
      else return null;
    }
  }

  private String getTestName(String prefix, MethodInvocation invocation) {
    String name = getTargetString(invocation.target(), "");
    return prefix == null || name == null ? null : prefix + " " + name;
  }
}
