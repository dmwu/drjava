/*BEGIN_COPYRIGHT_BLOCK
 *
 * This file is a part of DrJava. Current versions of this project are available
 * at http://sourceforge.net/projects/drjava
 *
 * Copyright (C) 2001-2002 JavaPLT group at Rice University (javaplt@rice.edu)
 *
 * DrJava is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrJava is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * or see http://www.gnu.org/licenses/gpl.html
 *
 * In addition, as a special exception, the JavaPLT group at Rice University
 * (javaplt@rice.edu) gives permission to link the code of DrJava with
 * the classes in the gj.util package, even if they are provided in binary-only
 * form, and distribute linked combinations including the DrJava and the
 * gj.util package. You must obey the GNU General Public License in all
 * respects for all of the code used other than these classes in the gj.util
 * package: Dictionary, HashtableEntry, ValueEnumerator, Enumeration,
 * KeyEnumerator, Vector, Hashtable, Stack, VectorEnumerator.
 *
 * If you modify this file, you may extend this exception to your version of the
 * file, but you are not obligated to do so. If you do not wish to
 * do so, delete this exception statement from your version. (However, the
 * present version of DrJava depends on these classes, so you'd want to
 * remove the dependency first!)
 *
END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.ui.config;

import javax.swing.*;
import edu.rice.cs.drjava.config.*;
import edu.rice.cs.drjava.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

import gj.util.Vector;


/**
 * Graphical form of a VectorOption
 * @version $Id$
 */
public class VectorOptionComponent extends OptionComponent<VectorOption<String>> 
  implements OptionConstants{  
  private JScrollPane _listScrollPane;
  private JPanel _panel;
  private JList _list;
  private JButton _addButton;
  private JButton _removeButton;
  private JButton _moveUpButton;
  private JButton _moveDownButton;
  private DefaultListModel _listModel;
  
  public VectorOptionComponent (VectorOption opt, String text, Frame parent) {
    super(opt, text, parent);
    
    //set up list
    _listModel = new DefaultListModel();
    _list = new JList(_listModel);
    reset();
    /*
    Vector v = DrJava.CONFIG.getSetting(_option);
    String[] array = new String[v.size()];
    v.copyInto(array);
    //_list.setListData(array);
    for (int i = 0; i < array.length; i++) {
      _listModel.addElement(array[i]);
    }
    */
    
    _addButton = new JButton("Add");
    _addButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        chooseFile();
        _list.setSelectedIndex(_listModel.getSize() - 1);
      }
    });
    
    _removeButton = new JButton("Remove");
    _removeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        if (!_list.isSelectionEmpty()) {
          int index = _list.getSelectedIndex();
          _listModel.remove(index);
          if (index == _listModel.getSize()) { // we removed the last element
            if (index > 0) // and there's more than one element in the list
            _list.setSelectedIndex(index - 1);
          }
          else
            _list.setSelectedIndex(index);
        }
      }
    });
    
    _moveUpButton = new JButton("Move Up");
    _moveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        if (!_list.isSelectionEmpty()) {
          int index = _list.getSelectedIndex();
          if (index > 0) {
            Object o = _listModel.getElementAt(index);
            _listModel.remove(index);
            _listModel.insertElementAt(o, index - 1);
            _list.setSelectedIndex(index - 1);
          }
        }
      }
    });
    
    _moveDownButton = new JButton("Move Down");
    _moveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        if (!_list.isSelectionEmpty()) {
          int index = _list.getSelectedIndex();
          if (index < _listModel.getSize() - 1) {
            Object o = _listModel.getElementAt(index);
            _listModel.remove(index);
            _listModel.insertElementAt(o, index + 1);
            _list.setSelectedIndex(index + 1);
          }
        }
      }
    });
    
    JPanel buttons = new JPanel();
    //buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
    //buttons.add(Box.createGlue());
    buttons.add(_addButton);
    buttons.add(_removeButton);
    //buttons.add(Box.createGlue());
    buttons.add(_moveUpButton);
    buttons.add(_moveDownButton);
    //buttons.add(Box.createGlue());
    
    _listScrollPane = new JScrollPane(_list,
                                      JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                      JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    
    _panel = new JPanel(new BorderLayout());
    _panel.add(_listScrollPane, BorderLayout.CENTER);
    _panel.add(buttons, BorderLayout.SOUTH);
  }
  
  public boolean update() {
    Vector current = new Vector();
    for (int i = 0; i < _listModel.getSize(); i++) {
      current.addElement(_listModel.getElementAt(i));
    }
    DrJava.CONFIG.setSetting(_option, current);
    reset();
    
    return true;
  } 
 
  public void reset() {
    Vector v = DrJava.CONFIG.getSetting(_option);
    String[] array = new String[v.size()];
    v.copyInto(array);
    _listModel.clear();
    for (int i = 0; i < array.length; i++) {
      _listModel.addElement(array[i]);
    }
  }
  
  public JComponent getComponent() { return _panel; }
  
  public void chooseFile() {
    String workDir = DrJava.CONFIG.getSetting(WORKING_DIRECTORY).toString();
    if ((workDir == null) || (workDir.equals(""))) {
      workDir = System.getProperty("user.dir");
    }
    JFileChooser jfc = new JFileChooser(workDir);
    jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    File c = null;
    int returnValue = jfc.showDialog(_parent,
                                     null);
    if (returnValue == JFileChooser.APPROVE_OPTION) 
      c = jfc.getSelectedFile();
    if (c != null) {
      _listModel.addElement(c.toString());
    }
    
  }
}