package org.jetbrains.plugins.scala.project.settings;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.EditableTreeModel;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

// TODO This is an almost exact clone of com.intellij.compiler.options.AnnotationProcessorsPanel
// TODO We may want either to extract a common "profile editor" class in IDEA platform,
// TODO or to rewrite this class in Scala (to improve the code quality and the UX).

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked", "UseOfObsoleteCollectionType"})
public class ScalaCompilerProfilesPanel extends JPanel {
  private final ScalaCompilerSettingsProfile myDefaultProfile = new ScalaCompilerSettingsProfile("");
  private final List<ScalaCompilerSettingsProfile> myModuleProfiles = new ArrayList<ScalaCompilerSettingsProfile>();
  private final Map<String, Module> myAllModulesMap = new HashMap<String, Module>();
  private final Project myProject;
  private final Tree myTree;
  private final ScalaCompilerSettingsPanel mySettingsPanel;
  private ScalaCompilerSettingsProfile mySelectedProfile = null;

  public ScalaCompilerProfilesPanel(Project project) {
    super(new BorderLayout());
    Splitter splitter = new Splitter(false, 0.3f);
    add(splitter, BorderLayout.CENTER);
    myProject = project;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      myAllModulesMap.put(module.getName(), module);
    }
    myTree = new Tree(new MyTreeModel());
    myTree.setRootVisible(false);
    final JPanel treePanel =
        ToolbarDecorator.createDecorator(myTree).addExtraAction(new AnActionButton("Move to", AllIcons.Actions.Nextfile) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            final MyModuleNode node = (MyModuleNode)myTree.getSelectionPath().getLastPathComponent();
            final TreePath[] selectedNodes = myTree.getSelectionPaths();
            final ScalaCompilerSettingsProfile nodeProfile = ((ProfileNode)node.getParent()).myProfile;
            final List<ScalaCompilerSettingsProfile> profiles = new ArrayList<ScalaCompilerSettingsProfile>();
            profiles.add(myDefaultProfile);
            for (ScalaCompilerSettingsProfile profile : myModuleProfiles) {
              profiles.add(profile);
            }
            profiles.remove(nodeProfile);
            final JBList list = new JBList(profiles);
            final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
                .setTitle("Move to")
                .setItemChoosenCallback(new Runnable() {
                  @Override
                  public void run() {
                    final Object value = list.getSelectedValue();
                    if (value instanceof ScalaCompilerSettingsProfile) {
                      final ScalaCompilerSettingsProfile chosenProfile = (ScalaCompilerSettingsProfile)value;
                      final Module toSelect = (Module)node.getUserObject();
                      if (selectedNodes != null) {
                        for (TreePath selectedNode : selectedNodes) {
                          final Object node = selectedNode.getLastPathComponent();
                          if (node instanceof MyModuleNode) {
                            final Module module = (Module)((MyModuleNode)node).getUserObject();
                            if (nodeProfile != myDefaultProfile) {
                              nodeProfile.removeModuleName(module.getName());
                            }
                            if (chosenProfile != myDefaultProfile) {
                              chosenProfile.addModuleName(module.getName());
                            }
                          }
                        }
                      }

                      final RootNode root = (RootNode)myTree.getModel().getRoot();
                      root.sync();
                      final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, toSelect);
                      if (node != null) {
                        TreeUtil.selectNode(myTree, node);
                      }
                    }
                  }
                })
                .createPopup();
            RelativePoint point =
                e.getInputEvent() instanceof MouseEvent ? getPreferredPopupPoint() : TreeUtil.getPointForSelection(myTree);
            popup.show(point);
          }

          @Override
          public ShortcutSet getShortcut() {
            return ActionManager.getInstance().getAction("Move").getShortcutSet();
          }

          @Override
          public boolean isEnabled() {
            return myTree.getSelectionPath() != null
                && myTree.getSelectionPath().getLastPathComponent() instanceof MyModuleNode
                && !myModuleProfiles.isEmpty();
          }
        }).createPanel();
    splitter.setFirstComponent(treePanel);
    myTree.setCellRenderer(new MyCellRenderer());

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
          Object node = path.getLastPathComponent();
          if (node instanceof MyModuleNode) {
            node = ((MyModuleNode)node).getParent();
          }
          if (node instanceof ProfileNode) {
            final ScalaCompilerSettingsProfile nodeProfile = ((ProfileNode)node).myProfile;
            final ScalaCompilerSettingsProfile selectedProfile = mySelectedProfile;
            if (nodeProfile != selectedProfile) {
              if (selectedProfile != null) {
                mySettingsPanel.saveTo(selectedProfile);
              }
              mySelectedProfile = nodeProfile;
              mySettingsPanel.setProfile(nodeProfile);
            }
          }
        }
      }
    });
    mySettingsPanel = new ScalaCompilerSettingsPanel();
    JPanel settingsComponent = mySettingsPanel.getComponent();
    settingsComponent.setBorder(IdeBorderFactory.createEmptyBorder(0, 6, 0, 0));
    splitter.setSecondComponent(settingsComponent);
  }

  public void initProfiles(ScalaCompilerSettingsProfile defaultProfile, Collection<ScalaCompilerSettingsProfile> moduleProfiles) {
    myDefaultProfile.initFrom(defaultProfile);
    myModuleProfiles.clear();
    for (ScalaCompilerSettingsProfile profile : moduleProfiles) {
      ScalaCompilerSettingsProfile copy = new ScalaCompilerSettingsProfile("");
      copy.initFrom(profile);
      myModuleProfiles.add(copy);
    }
    final RootNode root = (RootNode)myTree.getModel().getRoot();
    root.sync();
    final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, myDefaultProfile);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
    }

  }

  public ScalaCompilerSettingsProfile getDefaultProfile() {
    final ScalaCompilerSettingsProfile selectedProfile = mySelectedProfile;
    if (myDefaultProfile == selectedProfile) {
      mySettingsPanel.saveTo(selectedProfile);
    }
    return myDefaultProfile;
  }

  public List<ScalaCompilerSettingsProfile> getModuleProfiles() {
    final ScalaCompilerSettingsProfile selectedProfile = mySelectedProfile;
    if (myDefaultProfile != selectedProfile) {
      mySettingsPanel.saveTo(selectedProfile);
    }
    return myModuleProfiles;
  }

  private static void expand(JTree tree) {
    int oldRowCount = 0;
    do {
      int rowCount = tree.getRowCount();
      if (rowCount == oldRowCount) break;
      oldRowCount = rowCount;
      for (int i = 0; i < rowCount; i++) {
        tree.expandRow(i);
      }
    }
    while (true);
  }

  private class MyTreeModel extends DefaultTreeModel implements EditableTreeModel{
    public MyTreeModel() {
      super(new RootNode());
    }

    @Override
    public TreePath addNode(TreePath parentOrNeighbour) {
      final String newProfileName = Messages.showInputDialog(
          myProject, "Profile name", "Create new profile", null, "",
          new InputValidatorEx() {
            @Override
            public boolean checkInput(String inputString) {
              if (StringUtil.isEmpty(inputString) ||
                  Comparing.equal(inputString, myDefaultProfile.getName())) {
                return false;
              }
              for (ScalaCompilerSettingsProfile profile : myModuleProfiles) {
                if (Comparing.equal(inputString, profile.getName())) {
                  return false;
                }
              }
              return true;
            }

            @Override
            public boolean canClose(String inputString) {
              return checkInput(inputString);
            }

            @Override
            public String getErrorText(String inputString) {
              if (checkInput(inputString)) {
                return null;
              }
              return StringUtil.isEmpty(inputString)
                  ? "Profile name shouldn't be empty"
                  : "Profile " + inputString + " already exists";
            }
          });
      if (newProfileName != null) {
        final ScalaCompilerSettingsProfile profile = new ScalaCompilerSettingsProfile(newProfileName);
        myModuleProfiles.add(profile);
        ((DataSynchronizable)getRoot()).sync();
        final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), profile);
        if (object != null) {
          TreeUtil.selectNode(myTree, object);
        }
      }
      return null;
    }

    @Override
    public void removeNode(TreePath nodePath) {
      Object node = nodePath.getLastPathComponent();
      if (node instanceof ProfileNode) {
        final ScalaCompilerSettingsProfile nodeProfile = ((ProfileNode)node).myProfile;
        if (nodeProfile != myDefaultProfile) {
          if (mySelectedProfile == nodeProfile) {
            mySelectedProfile = null;
          }
          myModuleProfiles.remove(nodeProfile);
          ((DataSynchronizable)getRoot()).sync();
          final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), myDefaultProfile);
          if (object != null) {
            TreeUtil.selectNode(myTree, object);
          }
        }
      }
    }

    @Override
    public void removeNodes(Collection<TreePath> path) {
      // TODO looks like we don't need it
    }

    @Override
    public void moveNodeTo(TreePath parentOrNeighbour) {
    }

  }


  private class RootNode extends DefaultMutableTreeNode implements DataSynchronizable {
    @Override
    public DataSynchronizable sync() {
      final Vector newKids =  new Vector();
      newKids.add(new ProfileNode(myDefaultProfile, this, true).sync());
      for (ScalaCompilerSettingsProfile profile : myModuleProfiles) {
        newKids.add(new ProfileNode(profile, this, false).sync());
      }
      children = newKids;
      ((DefaultTreeModel)myTree.getModel()).reload();
      expand(myTree);
      return this;
    }
  }

  private interface DataSynchronizable {
    DataSynchronizable sync();
  }

  private class ProfileNode extends DefaultMutableTreeNode implements DataSynchronizable {
    private final ScalaCompilerSettingsProfile myProfile;
    private final boolean myIsDefault;

    public ProfileNode(ScalaCompilerSettingsProfile profile, RootNode parent, boolean isDefault) {
      super(profile);
      setParent(parent);
      myIsDefault = isDefault;
      myProfile = profile;
    }

    @Override
    public DataSynchronizable sync() {
      final List<Module> nodeModules = new ArrayList<Module>();
      if (myIsDefault) {
        final Set<String> nonDefaultProfileModules = new HashSet<String>();
        for (ScalaCompilerSettingsProfile profile : myModuleProfiles) {
          nonDefaultProfileModules.addAll(profile.getModuleNames());
        }
        for (Map.Entry<String, Module> entry : myAllModulesMap.entrySet()) {
          if (!nonDefaultProfileModules.contains(entry.getKey())) {
            nodeModules.add(entry.getValue());
          }
        }
      }
      else {
        for (String moduleName : myProfile.getModuleNames()) {
          final Module module = myAllModulesMap.get(moduleName);
          if (module != null) {
            nodeModules.add(module);
          }
        }
      }
      Collections.sort(nodeModules, ModuleComparator.INSTANCE);
      final Vector vector = new Vector();
      for (Module module : nodeModules) {
        vector.add(new MyModuleNode(module, this));
      }
      children = vector;
      return this;
    }

  }

  private static class MyModuleNode extends DefaultMutableTreeNode {
    public MyModuleNode(Module module, ScalaCompilerProfilesPanel.ProfileNode parent) {
      super(module);
      setParent(parent);
      setAllowsChildren(false);
    }
  }

  private static class MyCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof ProfileNode) {
        append(((ProfileNode)value).myProfile.getName());
      }
      else if (value instanceof MyModuleNode) {
        final Module module = (Module)((MyModuleNode)value).getUserObject();
        setIcon(AllIcons.Nodes.Module);
        append(module.getName());
      }
    }
  }

  private static class ModuleComparator implements Comparator<Module> {
    static final ModuleComparator INSTANCE = new ModuleComparator();
    @Override
    public int compare(Module o1, Module o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

}
