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

import de.tobifleig.lxc.data.LXCFile;
import de.tobifleig.lxc.data.VirtualFile;
import de.tobifleig.lxc.log.LXCLogBackend;
import de.tobifleig.lxc.log.LXCLogger;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The actual file-sender.
 *
 * @author Tobias Fleig <tobifleig googlemail com>
 */
public class Seeder extends Transceiver {

    private final LXCLogger logger;

    @Override
    public void run() {
        logger.info("Starting transmission... (version " + transVersion + ")");
        long startTime = System.currentTimeMillis();
        boolean transferOk = true;
        boolean fileBroken = false;
        try {
            List<VirtualFile> files = file.getFiles();
            ArrayList<VirtualFile> allList = new ArrayList<VirtualFile>();
            allList.addAll(files);

            for (int i = 0; i < allList.size(); i++) {
                VirtualFile currentFile = allList.get(i);
                if (currentFile.isDirectory()) {
                    // add files recursively
                    allList.addAll(currentFile.children());
                    // send directory itself
                    out.writeByte(files.contains(currentFile) ? 'D' : 'd');
                    out.writeUTF(currentFile.getTransferPath());
                } else {
                    // transfer a file
                    try {
                        InputStream rawInput = currentFile.getInputStream();
                        // inform client
                        out.writeByte(files.contains(currentFile) ? 'F' : 'f');
                        // send date, if version >= 1
                        if (transVersion >= 1) {
                            out.writeLong(currentFile.lastModified());
                        }
                        // send size
                        out.writeLong(currentFile.size());
                        // send path
                        out.writeUTF(currentFile.getTransferPath());
                        out.flush();
                        // send content (real data)
                        BufferedInputStream filein = null;
                        try {
                            filein = new BufferedInputStream(rawInput, 8388608);
                            byte[] buffer = new byte[4096];
                            int gotbytes;
                            while ((gotbytes = filein.read(buffer)) > 0) {
                                transferredBytes += gotbytes;
                                updateProgress();
                                out.write(buffer, 0, gotbytes);
                                out.flush();
                            }
                            // done
                            out.flush();
                        } catch (FileNotFoundException ex) {
                            logger.error("Unexpected FileNotFoundException during transfer", ex);
                        } finally {
                            try {
                                if (filein != null) {
                                    filein.close();
                                }
                            } catch (IOException ex) {
                                // ignore
                            }
                        }
                    } catch (FileNotFoundException ex) {
                        logger.error("Cannot upload file with transferpath \"" + currentFile.getTransferPath() + "\", file no longer exists - aborting transfer.");
                        // Cannot read file, send sorry (newer clients (>=149) display a message; older clients silently discard this.
                        out.writeByte('s');
                        out.flush();
                        // Abort transfer gracefully
                        transferOk = false;
                        fileBroken = true;
                        break;
                    }
                }
            }
            // transfer complete
            out.writeByte('e');
            out.flush();
        } catch (IOException ex) {
            if (!abort) {
                logger.error("Unexpected IException", ex);
            }
            transferOk = false;
        } finally {
            try {
                out.close();
                in.close();
                socket.close();
                System.gc(); // Closes filehandles etc
            } catch (Exception ex) {
                // Who cares..
            }
        }

        if (transferOk) {
            logger.info("Finished in " + (System.currentTimeMillis() - startTime) + "ms, speed was " + String.format(Locale.US, "%.2f", 1.0 * totalBytes / (System.currentTimeMillis() - startTime)) + "kb/s");
            listener.finished(true, abort, false, null);
            logger.info("Done seeding.");
        } else {
            listener.finished(false, abort, fileBroken,null);
            logger.error("Lost connection, aborting.");
        }

    }

    /**
     * Creates a new Seeder with the given parameters.
     * It is assumed the Streams are fully established
     * This means in particular:
     * - Both Streams are connected to a remote Leecher
     * - The remote side has been preconfigured and can start receiving files immediately
     * Please note:
     * This implementation uses two streams (intput+output) for Seeder and Leechers.
     * Obviously only one stream should be required (output for seed, input for leech)
     * Removing the second stream would break compatibilty with previous versions, therefore it is kept.
     * However, it should not be used and may be removed in the future.
     *
     * @param socket the socket
     * @param output the OutputStream, connected and ready
     * @param input the InputStream, connected and ready
     * @param transFile the {@link LXCFile} that is to be transferred
     * @param transVersion version of the transfer protocol {@link Transceiver}
     * @see Transceiver
     */
    public Seeder(Socket socket, ObjectOutputStream output, ObjectInputStream input, LXCFile transFile, int transVersion) {
        logger = LXCLogBackend.getLogger("seed-" + transFile.id);
        this.socket = socket;
        this.file = transFile;
        this.out = output;
        this.in = input;
        this.transVersion = transVersion;
        this.totalBytes = file.getFileSize();
    }

    /**
     * Starts this Leecher.
     * Creates a new thread, returns immediately.
     */
    @Override
    public void start() {
        Thread thread = new Thread(this);
        thread.setName("seeder_" + file.getShownName());
        thread.start();
    }

    @Override
    public void abort() {
        // Just kill it
        logger.info("Leecher: Aborting upload upon user-request.");
        abort = true;
        try {
            in.close();
        } catch (Exception ex) {
            // ignore
        }
    }
}
