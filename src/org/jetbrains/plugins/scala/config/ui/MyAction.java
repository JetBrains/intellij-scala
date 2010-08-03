package org.jetbrains.plugins.scala.config.ui;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pavel.Fatin, 02.08.2010
 */
public abstract class MyAction extends AbstractAction implements ListSelectionListener {
  private static final Pattern MNEMONIC_PATTERN = Pattern.compile("(?<=&)\\w");
  private String myId;

  protected MyAction(String id, String name, int keyCode) {
    this(id, name, keyCode, 0);
  }
  
  protected MyAction(String id, String name, int keyCode, int modifiers) {
    myId = id;
    
    Matcher matcher = MNEMONIC_PATTERN.matcher(name);
    if(matcher.find()) {
      putValue(Action.MNEMONIC_KEY, (int) matcher.group().charAt(0));
    }
    
    putValue(Action.NAME, name.replaceAll("&", ""));
    putValue(AbstractAction.ACCELERATOR_KEY, KeyStroke.getKeyStroke(keyCode, modifiers ));
  }

  public void registerOn(JComponent component){
    registerOn(component, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }
  
  public void registerOn(JComponent component, int condition){
    component.getActionMap().put(myId, this);
    component.getInputMap(condition).put((KeyStroke) getValue(AbstractAction.ACCELERATOR_KEY), myId);
  }
  
  public boolean isActive() {
    return true;
  }
  
  public void update() {
    setEnabled(isActive());
  }

  public void valueChanged(ListSelectionEvent e) {
    update();
  }
  
  public void perform() {
    actionPerformed(new ActionEvent("source stub", 0, ""));
  }
}

