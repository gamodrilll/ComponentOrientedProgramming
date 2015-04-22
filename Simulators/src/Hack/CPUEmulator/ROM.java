/********************************************************************************
 * The contents of this file are subject to the GNU General Public License      *
 * (GPL) Version 2 or later (the "License"); you may not use this file except   *
 * in compliance with the License. You may obtain a copy of the License at      *
 * http://www.gnu.org/copyleft/gpl.html                                         *
 *                                                                              *
 * Software distributed under the License is distributed on an "AS IS" basis,   *
 * without warranty of any kind, either expressed or implied. See the License   *
 * for the specific language governing rights and limitations under the         *
 * License.                                                                     *
 *                                                                              *
 * This file was originally developed as part of the software suite that        *
 * supports the book "The Elements of Computing Systems" by Nisan and Schocken, *
 * MIT Press 2005. If you modify the contents of this file, please document and *
 * mark your changes clearly, for the benefit of others.                        *
 ********************************************************************************/

package Hack.CPUEmulator;

import java.util.*;

import Hack.Assembler.*;

/**
 * A Read Only Memory. Has methods for loading a machine language file (.hack) and for
 * setting a pointer (a specific address in the ROM for GUI perposes).
 */
public class ROM extends ComputerParts.PointedMemory implements Events.ProgramEventListener
{
    /**
     * Decimal numeric format
     */
    public static final int DECIMAL_FORMAT = Controller.HackController.DECIMAL_FORMAT;

    /**
     * Hexadecimal numeric format
     */
    public static final int HEXA_FORMAT = Controller.HackController.HEXA_FORMAT;;

    /**
     * Binary numeric format
     */
    public static final int BINARY_FORMAT = Controller.HackController.BINARY_FORMAT;;

    /**
     * Assembler format
     */
    public static final int ASM_FORMAT = 4;

    // listeners to program changes
    private Vector listeners;

    /**
     * Constructs a new ROM with the given ROM GUI.
     */
    public ROM(ROMGUI gui) {
        super(Utilities.Definitions.ROM_SIZE, gui);
        setNullValue(HackAssemblerTranslator.NOP, true);
        listeners = new Vector();

        if (hasGUI) {
          gui.addProgramListener( (Events.ProgramEventListener)this);
          gui.setNumericFormat(ASM_FORMAT); // enable assembler
//          gui.setNumericFormat(BINARY_FORMAT); // disable assembler
        }
    }

    /**
     * Loads the given program file (HACK or ASM) into the ROM.
     */
    public synchronized void loadProgram(String fileName) throws Controller.ProgramException {
        short[] program = null;

        if (displayChanges)
            ((ROMGUI)gui).showMessage("Loading...");

        try {
            program = HackAssemblerTranslator.loadProgram(fileName, Utilities.Definitions.ROM_SIZE,
                                                          HackAssemblerTranslator.NOP);

            mem = program;

            if (displayChanges) {
                gui.setContents(mem);

                ((ROMGUI)gui).setProgram(fileName);

                ((ROMGUI)gui).hideMessage();
                gui.hideHighlight();
            }

            notifyProgramListeners(Events.ProgramEvent.LOAD, fileName);

        } catch (AssemblerException ae) {
            if (displayChanges)
                ((ROMGUI)gui).hideMessage();
            throw new Controller.ProgramException(ae.getMessage());
        }

    }

    /**
     * Called when the ROM's current program is changed.
     * The event contains the source object, event type and the new program's file name (if any).
     */
    public void programChanged(Events.ProgramEvent event) {
        switch (event.getType()) {
            case Events.ProgramEvent.LOAD:
                ROMLoadProgramTask task = new ROMLoadProgramTask(event.getProgramFileName());
                Thread t = new Thread(task);
                t.start();
                break;
            case Events.ProgramEvent.CLEAR:
                notifyProgramListeners(Events.ProgramEvent.CLEAR, null);
        }
    }

    /**
     * Called when the contents of the memory are changed through the memory gui.
     */
    public void valueChanged(ComputerParts.ComputerPartEvent event) {
        short newValue = event.getValue();
        int newAddress = event.getIndex();
        clearErrorListeners();
        try {
            HackAssemblerTranslator.getInstance().codeToText(newValue);
            setValueAt(newAddress, newValue, true);
        } catch (AssemblerException ae) {
            notifyErrorListeners("Illegal instruction");
            quietUpdateGUI(newAddress, mem[newAddress]);
        }
    }

    class ROMLoadProgramTask implements Runnable {

        private String programName;

        public ROMLoadProgramTask(String programName) {
            this.programName = programName;
        }

        public void run() {
            clearErrorListeners();
            try {
                loadProgram(programName);
            } catch (Controller.ProgramException pe) {
                notifyErrorListeners(pe.getMessage());
            }
        }
    }

    /**
     * Registers the given ProgramEventListener as a listener to this GUI.
     */
    public void addProgramListener(Events.ProgramEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Un-registers the given ProgramEventListener from being a listener to this GUI.
     */
    public void removeProgramListener(Events.ProgramEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all the ProgramEventListeners on a change in the ROM's program by creating
     * a ProgramEvent (with the new event type and program's file name) and sending it using the
     * programChanged method to all the listeners.
     */
    protected void notifyProgramListeners(byte eventType, String programFileName) {
        Events.ProgramEvent event = new Events.ProgramEvent(this, eventType, programFileName);

        for (int i = 0; i < listeners.size(); i++) {
            ((Events.ProgramEventListener)listeners.elementAt(i)).programChanged(event);
        }
    }
}
