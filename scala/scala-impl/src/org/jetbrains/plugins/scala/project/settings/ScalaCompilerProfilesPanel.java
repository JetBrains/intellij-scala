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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.EditableTreeModel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

/**
 * TODO This is an almost exact clone of {@link com.intellij.compiler.options.AnnotationProcessorsPanel}
 *  We may want either to extract a common "profile editor" class in IDEA platform,
 *  OR
 *  rewrite this class in Scala (to improve the code quality and the UX).
 */
public class ScalaCompilerProfilesPanel extends JPanel {

  private final ScalaCompilerSettingsProfile myDefaultProfile = new ScalaCompilerSettingsProfile("");
  private final List<ScalaCompilerSettingsProfile> myModuleProfiles = new ArrayList<>();
  private final Map<String, Module> myAllModulesMap = new HashMap<>();
  private final Project myProject;
  private final Tree myTree; // left panel
  private final ScalaCompilerSettingsPanel mySettingsPanel; // right panel

  // used like a global variable within a project to pass context about which profile should we select on settings panel open
  // not intended to be persisted (for now) so should be reset once read
  public static final Key<String> SELECTED_PROFILE_NAME = new Key<>("SelectedScalaCompilerProfileName");

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
    final JPanel treePanel = ToolbarDecorator
            .createDecorator(myTree)
            .addExtraAction(new MoveToAction(myTree, myDefaultProfile, myModuleProfiles))
            .createPanel();
    splitter.setFirstComponent(treePanel);

    myTree.setCellRenderer(new MyCellRenderer());

    mySettingsPanel = new ScalaCompilerSettingsPanel();

    myTree.addTreeSelectionListener(e -> {
      ProfileNode selectedNode = getSelectedNode(myTree);
      if (selectedNode == null) return;
      final ScalaCompilerSettingsProfile nodeProfile = selectedNode.myProfile;
      final ScalaCompilerSettingsProfile selectedProfile = mySelectedProfile;
      if (nodeProfile != selectedProfile) {
        if (selectedProfile != null) {
          mySettingsPanel.saveTo(selectedProfile);
        }
        mySelectedProfile = nodeProfile;
        mySettingsPanel.setProfile(nodeProfile);
      }
    });

    JPanel settingsComponent = mySettingsPanel.getComponent();
    settingsComponent.setBorder(JBUI.Borders.emptyLeft(6));
    splitter.setSecondComponent(settingsComponent);

    final TreeSpeedSearch search = new TreeSpeedSearch(myTree);
    search.setComparator(new SpeedSearchComparator(false));
  }


  @Nullable
  private ProfileNode getSelectedNode(Tree tree) {
    final TreePath path = tree.getSelectionPath();
    if (path == null) return null;

    Object node = path.getLastPathComponent();
    if (node instanceof MyModuleNode) {
      node = ((MyModuleNode)node).getParent();
    }
    if (node instanceof ProfileNode) {
      return ((ProfileNode) node);
    } else {
      return null;
    }
  }

  private static class MoveToAction extends AnActionButton {

    private final Tree tree;
    private final ScalaCompilerSettingsProfile defaultProfile;
    private final List<ScalaCompilerSettingsProfile> moduleProfiles;

    public MoveToAction(Tree tree, ScalaCompilerSettingsProfile defaultProfile, List<ScalaCompilerSettingsProfile> moduleProfiles) {
      super("Move to", AllIcons.Actions.Forward);
      this.tree = tree;
      this.defaultProfile = defaultProfile;
      this.moduleProfiles = moduleProfiles;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      TreePath selectionPath = tree.getSelectionPath();
      if (selectionPath == null) return;
      final MyModuleNode node = (MyModuleNode) selectionPath.getLastPathComponent();
      final TreePath[] selectedNodes = tree.getSelectionPaths();
      final ScalaCompilerSettingsProfile nodeProfile = ((ProfileNode)node.getParent()).myProfile;

      final List<ScalaCompilerSettingsProfile> profiles = new ArrayList<>();
      profiles.add(defaultProfile);
      profiles.addAll(moduleProfiles);
      profiles.remove(nodeProfile);

      final JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(new ArrayList<>(profiles))
              .setTitle("Move to")
              .setItemChosenCallback(selectedProfile -> {
                if (selectedProfile == null) return;

                final Module toSelect = (Module) node.getUserObject();
                if (selectedNodes != null) {
                  for (TreePath selectedNode : selectedNodes) {
                    final Object node1 = selectedNode.getLastPathComponent();
                    if (node1 instanceof MyModuleNode) {
                      final Module module = (Module) ((MyModuleNode) node1).getUserObject();
                      if (nodeProfile != defaultProfile) {
                        nodeProfile.removeModuleName(module.getName());
                      }
                      if (selectedProfile != defaultProfile) {
                        selectedProfile.addModuleName(module.getName());
                      }
                    }
                  }
                }

                final RootNode root = (RootNode) tree.getModel().getRoot();
                root.sync();
                final DefaultMutableTreeNode node1 = TreeUtil.findNodeWithObject(root, toSelect);
                if (node1 != null) {
                  TreeUtil.selectNode(tree, node1);
                }
              })
              .createPopup();

      RelativePoint point = getRelativePoint(e);
      popup.show(point);
    }

    @NotNull
    private RelativePoint getRelativePoint(@NotNull AnActionEvent e) {
      RelativePoint point = e.getInputEvent() instanceof MouseEvent
              ? getPreferredPopupPoint()
              : TreeUtil.getPointForSelection(tree);
      if (point == null)
        point = TreeUtil.getPointForSelection(tree);
      return point;
    }

    @Override
    public ShortcutSet getShortcut() {
      return ActionManager.getInstance().getAction("Move").getShortcutSet();
    }

    @Override
    public boolean isEnabled() {
      return tree.getSelectionPath() != null
              && tree.getSelectionPath().getLastPathComponent() instanceof MyModuleNode
              && !moduleProfiles.isEmpty();
    }
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
    preselectProfile(root);
  }

  private void preselectProfile(ScalaCompilerProfilesPanel.RootNode root) {
    String profileNameToSelect = myProject.getUserData(SELECTED_PROFILE_NAME);
    final DefaultMutableTreeNode nodeToSelect = profileNameToSelect != null
            ? findProfileNodeWithName(root, profileNameToSelect)
            : TreeUtil.findNodeWithObject(root, myDefaultProfile);
    if (nodeToSelect != null) {
      TreeUtil.selectNode(myTree, nodeToSelect);
      myProject.putUserData(SELECTED_PROFILE_NAME, null);
    }
  }

  private DefaultMutableTreeNode findProfileNodeWithName(ScalaCompilerProfilesPanel.RootNode root, String profileName) {
    return TreeUtil.findNode(root, n -> {
      if (n instanceof ProfileNode) {
        return ((ProfileNode) n).myProfile.getName().equals(profileName);
      }
      return false;
    });
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

  private class MyTreeModel extends DefaultTreeModel implements EditableTreeModel {
    public MyTreeModel() {
      super(new RootNode());
    }

    @Override
    public TreePath addNode(TreePath parentOrNeighbour) {
      final String newProfileName = Messages.showInputDialog(
              myProject, "Profile name", "Create New Profile", null, "",
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
    public void removeNodes(Collection<? extends TreePath> path) {
      // TODO looks like we don't need it
    }

    @Override
    public void moveNodeTo(TreePath parentOrNeighbour) {
    }

  }

  private class RootNode extends DefaultMutableTreeNode implements DataSynchronizable {
    @Override
    public DataSynchronizable sync() {
      final Vector<DataSynchronizable> newKids =  new Vector<>();
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
      final List<Module> nodeModules = new ArrayList<>();
      if (myIsDefault) {
        final Set<String> nonDefaultProfileModules = new HashSet<>();
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
      nodeModules.sort(ModuleComparator.INSTANCE);
      final Vector<MyModuleNode> vector = new Vector<>();
      for (Module module : nodeModules) {
        vector.add(new MyModuleNode(module, this));
      }
      children = vector;
      return this;
    }

  }

  private static class MyModuleNode extends DefaultMutableTreeNode {
    public MyModuleNode(Module module, ProfileNode parent) {
      super(module);
      setParent(parent);
      setAllowsChildren(false);
    }
  }

  private static class MyCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
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
