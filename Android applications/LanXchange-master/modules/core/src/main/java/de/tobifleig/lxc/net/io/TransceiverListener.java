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
package de.tobifleig.lxc.net.io;

/**
 * Used to Listen for Transceiver-Events.
 *
 * @author Tobias Fleig <tobifleig googlemail com>
 */
public interface TransceiverListener {

    /**
     * Called by Transceiver upon completion of the download/upload.
     * @param success true, if successful, false if cancelled/interrupted
     * @param cancelled true, if the job was cancelled by the user (i.e. don't display any error messages)
     * @param removeFile if true, the error was severe and the file should no longer be offered (only used by seeders)
     * @param problemFilePath path of problematic file, if a file caused a transfer abort/cancel (null otherwise)
     */
    public void finished(boolean success, boolean cancelled, boolean removeFile, String problemFilePath);
}
