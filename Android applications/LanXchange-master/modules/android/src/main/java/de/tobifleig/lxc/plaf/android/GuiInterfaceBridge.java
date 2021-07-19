/*
 * Copyright 2009, 2010, 2011, 2012, 2013, 2014 Tobias Fleig (tobifleig gmail com)
 *
 * All rights reserved.
 *
 * This file is part of LanXchange.
 *
 * LanXchange is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LanXchange is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LanXchange. If not, see <http://www.gnu.org/licenses/>.
 */
package de.tobifleig.lxc.plaf.android;

import android.content.Context;
import de.tobifleig.lxc.data.LXCFile;

import java.util.List;

/**
 * Required to forward some events from LXCService (that implements GuiInterface)
 * to the real Gui (that LXC cannot handle because it can be recreated etc)
 * @author tfg
 *
 */
public interface GuiInterfaceBridge {

    /**
     * Forwards some update()-calls to the gui.
     */
    public void update();

    /**
     * Forwards some notifyFileChange-calls to the gui.
     */
    public void notifyFileChange(int fileOrigin, int operation, int firstIndex, int numberOfFiles, List<LXCFile> affectedFiles);

    /**
     * Forwards notifiyJobChange-calls to the gui.
     */
    public void notifyJobChange(int operation, LXCFile file, int index);

    /**
     * Forwards confirmCloseWithTransfersRunning()-calls to the gui.
     */
    public boolean confirmCloseWithTransfersRunning();

    /**
     * Forwards error-messages from the LanXchange core.
     * Displays errors on a best-effort basis only!
     *
     * @param context The context of the background service, required to create a notification
     * @param error The error message
     */
    public void showError(Context context, String error);

}
