package psimulator.userInterface;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.Observable;
import java.util.Observer;
import javax.swing.*;
import psimulator.dataLayer.DataLayerFacade;
import psimulator.userInterface.SimulatorEditor.DrawPanel.Enums.UndoRedo;
import psimulator.userInterface.SimulatorEditor.DrawPanel.Enums.Zoom;

/**
 *
 * @author Martin
 */
public class MenuBar extends JMenuBar implements Observer {

    private DataLayerFacade dataLayer;

    private JMenu jMenuFile;
    private JMenuItem jMenuItemNew;
    private JMenuItem jMenuItemClose;
    private JMenuItem jMenuItemOpen;
    private JMenuItem jMenuItemSave;        
    private JMenuItem jMenuItemSaveAs;
    private JMenuItem jMenuItemExit;
    
    private JMenu jMenuEdit;
    private JMenuItem jMenuItemUndo;        
    private JMenuItem jMenuItemRedo;
    private JMenuItem jMenuItemSelectAll;
    private JMenuItem jMenuItemDelete;
    
    private JMenu jMenuView;
    private JMenuItem jMenuItemZoomIn;
    private JMenuItem jMenuItemZoomOut;
    private JMenuItem jMenuItemZoomReset;        
    
    private JMenu jMenuOptions;
    private JMenuItem jMenuItemPreferences;

    public MenuBar(DataLayerFacade dataLayer) {
        super();
        this.dataLayer = dataLayer;

        // add this MenuBar as observer to langage manager
        dataLayer.addLanguageObserver((Observer)this);
        
        /* menu File */
        jMenuFile = new JMenu();
        
        jMenuItemNew = new JMenuItem();
        jMenuItemNew.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/filenew.png")));
        jMenuItemClose = new JMenuItem();
        jMenuItemClose.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/fileclose.png")));
        jMenuItemOpen = new JMenuItem();
        jMenuItemOpen.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/folder_green_open.png")));
        jMenuItemSave = new JMenuItem(); 
        jMenuItemSave.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/filesave.png")));
        jMenuItemSaveAs = new JMenuItem();
        jMenuItemSaveAs.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/filesaveas.png")));
        jMenuItemExit = new JMenuItem();
        
        jMenuFile.add(jMenuItemNew);
        jMenuFile.add(jMenuItemClose);
        jMenuFile.addSeparator();
        jMenuFile.add(jMenuItemOpen);
        jMenuFile.add(jMenuItemSave);
        jMenuFile.add(jMenuItemSaveAs);
        jMenuFile.addSeparator();
        jMenuFile.add(jMenuItemExit);
        /* END menu File */

        /* menu Edit */
        jMenuEdit = new JMenu();
        
        jMenuItemUndo = new JMenuItem();
        jMenuItemUndo.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/undo.png")));
        jMenuItemUndo.setActionCommand(UndoRedo.UNDO.toString());
        
        jMenuItemRedo = new JMenuItem();
        jMenuItemRedo.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/redo.png")));
        jMenuItemRedo.setActionCommand(UndoRedo.REDO.toString());
        
        
        //jMenuItemSelectAll = new JMenuItem();
        //jMenuItemDelete = new JMenuItem();
        
        jMenuEdit.add(jMenuItemUndo);
        jMenuEdit.add(jMenuItemRedo);
        //jMenuEdit.addSeparator();
        //jMenuEdit.add(jMenuItemSelectAll);
        //jMenuEdit.add(jMenuItemDelete);
        
        /* END menu Edit */
        
        /* menu View */
        jMenuView = new JMenu();
        
        jMenuItemZoomIn = new JMenuItem();
        jMenuItemZoomIn.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/viewmag+.png")));
        jMenuItemZoomIn.setActionCommand(Zoom.IN.toString());
        jMenuItemZoomOut = new JMenuItem();
        jMenuItemZoomOut.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/viewmag-.png")));
        jMenuItemZoomOut.setActionCommand(Zoom.OUT.toString());
        jMenuItemZoomReset = new JMenuItem();
        jMenuItemZoomReset.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/viewmag1.png")));
        jMenuItemZoomReset.setActionCommand(Zoom.RESET.toString());
        
        jMenuView.add(jMenuItemZoomIn);
        jMenuView.add(jMenuItemZoomOut);
        jMenuView.add(jMenuItemZoomReset);
        /* END menu View */
        
        /* menu Options */
        jMenuOptions = new JMenu();
        jMenuItemPreferences = new JMenuItem();
        jMenuItemPreferences.setIcon(new ImageIcon(getClass().getResource("/resources/toolbarIcons/16/configure.png")));
        jMenuOptions.add(jMenuItemPreferences);
        /* END menu Options */
        
        
        /* add menus to menu bar */
        this.add(jMenuFile);
        this.add(jMenuEdit);
        this.add(jMenuView);
        this.add(jMenuOptions);

        /* set texts to menu items */
        setMnemonics();
        setTextsToComponents();
    }
    
    private void setMnemonics(){
        jMenuFile.setMnemonic('F');
        
        jMenuItemNew.setAccelerator(KeyStroke.getKeyStroke('N', InputEvent.CTRL_DOWN_MASK));
        jMenuItemClose.setAccelerator(KeyStroke.getKeyStroke('W', ActionEvent.CTRL_MASK));
        jMenuItemOpen.setAccelerator(KeyStroke.getKeyStroke('O', InputEvent.CTRL_DOWN_MASK));
        jMenuItemSave.setAccelerator(KeyStroke.getKeyStroke('S', ActionEvent.CTRL_MASK));
        jMenuItemSaveAs.setAccelerator(KeyStroke.getKeyStroke('S', InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        jMenuItemExit.setAccelerator(KeyStroke.getKeyStroke('Q', ActionEvent.CTRL_MASK));
        
        jMenuEdit.setMnemonic('E');
        jMenuItemUndo.setAccelerator(KeyStroke.getKeyStroke('Z', InputEvent.CTRL_DOWN_MASK));
        jMenuItemRedo.setAccelerator(KeyStroke.getKeyStroke('Y', InputEvent.CTRL_DOWN_MASK));
        //jMenuItemSelectAll.setAccelerator(KeyStroke.getKeyStroke('A', InputEvent.CTRL_DOWN_MASK));
        //jMenuItemDelete.setAccelerator(KeyStroke.getKeyStroke("DELETE"));
        
        jMenuView.setMnemonic('V');
        jMenuItemZoomIn.setAccelerator(KeyStroke.getKeyStroke("control ADD"));
        jMenuItemZoomOut.setAccelerator(KeyStroke.getKeyStroke("control SUBTRACT"));
        jMenuItemZoomReset.setAccelerator(KeyStroke.getKeyStroke("control NUMPAD0"));//, InputEvent.CTRL_DOWN_MASK));
        
        jMenuOptions.setMnemonic('O');
        jMenuItemPreferences.setAccelerator(KeyStroke.getKeyStroke('P', ActionEvent.CTRL_MASK));
        
    }

    private void setTextsToComponents() {
        /* menu File */
        jMenuFile.setText(dataLayer.getString("FILE"));
        jMenuItemNew.setText(dataLayer.getString("NEW_PROJECT"));
        jMenuItemClose.setText(dataLayer.getString("CLOSE"));
        jMenuItemOpen.setText(dataLayer.getString("OPEN"));
        jMenuItemSave.setText(dataLayer.getString("SAVE"));
        jMenuItemSaveAs.setText(dataLayer.getString("SAVE_AS"));
        jMenuItemExit.setText(dataLayer.getString("EXIT"));
        /* END menu File */
        
        /* menu Edit */
        jMenuEdit.setText(dataLayer.getString("EDIT"));
        
        jMenuItemUndo.setText(dataLayer.getString("UNDO"));
        jMenuItemRedo.setText(dataLayer.getString("REDO"));
        
        //jMenuItemSelectAll.setText(dataLayer.getString("SELECT_ALL"));
        
        //jMenuItemDelete.setText(dataLayer.getString("DELETE"));
        /* END menu Edit */
        
        /* menu View */
        jMenuView.setText(dataLayer.getString("VIEW"));
        
        jMenuItemZoomIn.setText(dataLayer.getString("ZOOM_IN"));
        jMenuItemZoomOut.setText(dataLayer.getString("ZOOM_OUT"));
        jMenuItemZoomReset.setText(dataLayer.getString("ZOOM_RESET"));
        /* END menu View */
        
        /* menu Options */
        jMenuOptions.setText(dataLayer.getString("OPTIONS"));
        
        jMenuItemPreferences.setText(dataLayer.getString("PREFERENCES"));
        /* END menu Options */

        
    }

    @Override
    public void update(Observable o, Object o1) {
        this.setTextsToComponents();
    }
    
    
    public void setUndoEnabled(boolean enabled){
        jMenuItemUndo.setEnabled(enabled);
    }
    
    public void setRedoEnabled(boolean enabled){
        jMenuItemRedo.setEnabled(enabled);
    }
    
    public void setZoomInEnabled(boolean enabled){
        jMenuItemZoomIn.setEnabled(enabled);
    }
    
    public void setZoomOutEnabled(boolean enabled){
        jMenuItemZoomOut.setEnabled(enabled);
    }
    
    public void setZoomResetEnabled(boolean enabled){
        jMenuItemZoomReset.setEnabled(enabled);
    }
    
    public void setProjectRelatedButtonsEnabled(boolean enabled){
        jMenuItemClose.setEnabled(enabled);
        jMenuItemSave.setEnabled(enabled);
        jMenuItemSaveAs.setEnabled(enabled);
    }

    /**
     * Adds action listener to jMenuItemNew
     * @param listener Action listener
     */
    public void addNewProjectActionListener(ActionListener listener){
        jMenuItemNew.addActionListener(listener);
    }
    
    /**
     * Adds action listener to jButtonClose
     * @param listener Action listener
     */
    public void addCloseActionListener(ActionListener listener){
        jMenuItemClose.addActionListener(listener);
    }
    
    /**
     * Adds action listener to jMenuItemOpen
     * @param listener Action listener
     */
    public void addOpenActionListener(ActionListener listener){
        jMenuItemOpen.addActionListener(listener);
    }
    
    /**
     * Adds action listener to jMenuItemSave
     * @param listener Action listener
     */
    public void addSaveActionListener(ActionListener listener){
        jMenuItemSave.addActionListener(listener);
    }
    
    /**
     * Adds action listener to jMenuItemSaveAs
     * @param listener Action listener
     */
    public void addSaveAsActionListener(ActionListener listener){
        jMenuItemSaveAs.addActionListener(listener);
    }
    
    /**
     * Adds action listener to jMenuItem Exit
     * @param listener Action listener
     */
    public void addExitActionListener(ActionListener listener){
        jMenuItemExit.addActionListener(listener);
    }
    
    /**
     * Adds action listener to jMenuItemUndo and jMenuItemRedo
     * @param listener Action listener
     */
    public void addUndoRedoActionListener(ActionListener listener){
        jMenuItemUndo.addActionListener(listener);
        jMenuItemRedo.addActionListener(listener);
    }
    
    /**
     * Adds action listener to Zoom menu items
     * @param listener Action listener
     */
    public void addZoomActionListener(ActionListener listener){
        jMenuItemZoomIn.addActionListener(listener);
        jMenuItemZoomOut.addActionListener(listener);
        jMenuItemZoomReset.addActionListener(listener);
    }
    
    /**
     * Adds action listener to Preferences jMenuItem
     * @param listener Action listener
     */
    public void addPreferencesActionListener(ActionListener listener){
        jMenuItemPreferences.addActionListener(listener);
    }
    
    public void addSelectAllListener(ActionListener listener){
        jMenuItemSelectAll.addActionListener(listener);
    }
    
    public void addDeleteListener(ActionListener listener){
        jMenuItemDelete.addActionListener(listener);
    }
}
