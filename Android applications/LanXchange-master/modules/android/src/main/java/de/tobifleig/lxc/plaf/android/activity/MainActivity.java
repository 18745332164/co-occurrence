/*
 * Copyright 2009, 2010, 2011, 2012, 2013, 2014, 2015 Tobias Fleig (tobifleig gmail com)
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
package de.tobifleig.lxc.plaf.android.activity;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.*;
import android.widget.TextView;
import de.tobifleig.lxc.R;
import de.tobifleig.lxc.data.LXCFile;
import de.tobifleig.lxc.data.VirtualFile;
import de.tobifleig.lxc.data.impl.InMemoryFile;
import de.tobifleig.lxc.data.impl.RealFile;
import de.tobifleig.lxc.log.LXCLogBackend;
import de.tobifleig.lxc.log.LXCLogger;
import de.tobifleig.lxc.plaf.GuiInterface;
import de.tobifleig.lxc.plaf.android.AndroidGuiListener;
import de.tobifleig.lxc.plaf.android.ConnectivityChangeListener;
import de.tobifleig.lxc.plaf.android.ConnectivityChangeReceiver;
import de.tobifleig.lxc.plaf.android.GuiInterfaceBridge;
import de.tobifleig.lxc.plaf.android.NonFileContent;
import de.tobifleig.lxc.plaf.android.PermissionTools;
import de.tobifleig.lxc.plaf.android.service.AndroidSingleton;
import de.tobifleig.lxc.plaf.android.ui.FileListView;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;
import net.rdrei.android.dirchooser.DirectoryChooserFragment;

/**
 * Platform for Android / Default Activity
 * 
 * no automated updates (managed by Google Play)
 * 
 * @author Tobias Fleig <tobifleig googlemail com>
 */
public class MainActivity extends AppCompatActivity implements CancelablePermissionPromptActivity {

    /**
     * Intent from service, requests LanXchange termination
     * This activity may be paused/stopped when the intent is received, so this cannot show a dialog if transfers
     * are running.
     */
    public static final String ACTION_STOP_FROMSERVICE = "de.tobifleig.lxc.plaf.android.activity.ACTION_STOP_FROMSERVICE";
    /**
     * Intent fired by this activity. Upon reception, the activity is visible, so dialogs can be used.
     */
    public static final String ACTION_STOP_FROMACTIVITY = "de.tobifleig.lxc.plaf.android.activity.ACTION_STOP_FROMACTIVITY";
    /**
     * Intent from notification created by service, requests display of LanXchange core error message.
     */
    public static final String ACTION_SHOW_ERROR = "de.tobifleig.lxc.plaf.android.activity.ACTION_SHOW_ERROR";

    private static final int RETURNCODE_FILEINTENT = 42;
    public static final int RETURNCODE_PERMISSION_PROMPT_STORAGE = 43;

    private LXCLogger logger;

    private AndroidGuiListener guiListener;
    private GuiInterfaceBridge guiBridge;

    /**
     * Holds the quickshare-List until the user answered the storage permission prompt.
     */
    private List<VirtualFile> permissionPromptQuickshare;
    /**
     * Holds the requested file to download until the user answered the storage permission prompt.
     */
    private LXCFile permissionPromptDownloadFile;
    /**
     * True, if the user tried to share a file when the storate permission prompt fired.
     */
    private boolean permissionPromptShareFile;
    /**
     * Handles network state changes (wifi coming online)
     */
    private ConnectivityChangeReceiver networkStateChangeReceiver;
    /**
     * The view that displays all shared and available files
     */
    private FileListView fileListView;
    /**
     * Marker to detect when we return from settings.
     */
    private boolean launchedSettings = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger = LXCLogBackend.getLogger("main-activity");

        // Check intent first
        List<VirtualFile> quickShare = null;
        Intent launchIntent = getIntent();
        if (launchIntent.getAction() != null && (launchIntent.getAction().equals(Intent.ACTION_SEND) || launchIntent.getAction().equals(Intent.ACTION_SEND_MULTIPLE))) {
            quickShare = computeInputIntent(launchIntent);
            if (quickShare == null) {
                // unable to access file, inform user
                handleShareError(launchIntent);
            }
            // disarm intent to prevent sharing the same file twice on screen rotate
            launchIntent.setAction(null);
        } else if (launchIntent.getAction() != null && launchIntent.getAction().equals(ACTION_SHOW_ERROR)) {
            showErrorDialog(this, launchIntent.getCharSequenceExtra(Intent.EXTRA_TEXT));
        }

        // load layout
        setContentView(R.layout.main);
        // layout is loaded, setup main view
        fileListView = (FileListView) findViewById(R.id.fileList);
        // set up action bar logo
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayUseLogoEnabled(true);
            getSupportActionBar().setLogo(R.drawable.ic_branding_white);
        }
        // set up the text displayed when there are no files
        TextView emptyText = (TextView) ((LayoutInflater) getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.empty_list, null);
        fileListView.setEmptyView(emptyText);
        ViewGroup root = (ViewGroup) findViewById(R.id.main_layout);
        CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        root.addView(emptyText, layoutParams);

        ConnectivityChangeReceiver.setConnectivityListener(new ConnectivityChangeListener() {

            @Override
            public void setWifiState(boolean isWifi) {
                setWifiWarning(!isWifi);
            }
        });
        networkStateChangeReceiver = new ConnectivityChangeReceiver();


        guiBridge = new GuiInterfaceBridge() {

            @Override
            public void update() {
                fileListView.updateGui();
            }

            @Override
            public void notifyFileChange(int fileOrigin, int operation, int firstIndex, int numberOfFiles, List<LXCFile> affectedFiles) {
                if (fileOrigin == GuiInterface.UPDATE_ORIGIN_LOCAL) {
                    // local implies: only 1 file at a time
                    if (operation == GuiInterface.UPDATE_OPERATION_ADD) {
                        fileListView.notifyLocalFileAdded();
                    } else if (operation == GuiInterface.UPDATE_OPERATION_REMOVE) {
                        fileListView.notifyLocalFileRemoved(firstIndex);
                    }
                } else if (fileOrigin == GuiInterface.UPDATE_ORIGIN_REMOTE) {
                    if (operation == GuiInterface.UPDATE_OPERATION_ADD) {
                        fileListView.notifyRemoteFilesAdded(numberOfFiles);
                    } else if (operation == GuiInterface.UPDATE_OPERATION_REMOVE) {
                        fileListView.notifyRemoteFilesRemoved(firstIndex, numberOfFiles);
                    }
                }
            }

            @Override
            public void notifyJobChange(int operation, LXCFile file, int index) {
                if (operation == GuiInterface.UPDATE_OPERATION_ADD) {
                    fileListView.notifyLocalJobAdded(file, index);
                } else if (operation == GuiInterface.UPDATE_OPERATION_REMOVE) {
                    fileListView.notifyLocalJobRemoved(file, index);
                }
            }

            @Override
            public boolean confirmCloseWithTransfersRunning() {
                AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.main_layout).getContext());
                builder.setMessage(R.string.dialog_closewithrunning_text);
                builder.setTitle(R.string.dialog_closewithrunning_title);
                builder.setPositiveButton(R.string.dialog_closewithrunning_yes, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        guiListener.shutdown(true, true, false);
                        AndroidSingleton.onRealDestroy();
                        finish();
                    }
                });
                builder.setNegativeButton(R.string.dialog_closewithrunning_no, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
                // always return false
                // if the user decides to kill lanxchange anyway, shutdown is called again
                return false;
            }

            @Override
            public void showError(Context context, String error) {
                View main = findViewById(R.id.main_layout);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        showErrorDialog(main.getContext(), error);
                    }
                });
            }
        };

        // setup floating action button
        findViewById(R.id.fab_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                permissionPromptShareFile = true;
                if (PermissionTools.verifyStoragePermission(MainActivity.this, MainActivity.this)) {
                    permissionPromptShareFile = false;
                    shareFile();
                }
            }
        });

        AndroidSingleton.onCreateMainActivity(this, guiBridge, quickShare);

        // now handle normal intents (ACTION_SEND* has been disarmed by deleting the action)
        if (launchIntent.getAction() != null) {
            onNewIntent(launchIntent);
        }
    }

    private void showErrorDialog(Context context, CharSequence error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.error_generic_from_core_title);
        builder.setMessage(error);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    private void shareFile() {
        Intent chooseFile = new Intent();
        chooseFile.setAction(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        if (android.os.Build.VERSION.SDK_INT >= 18) {
            chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        chooseFile.setType("*/*");
        startActivityForResult(chooseFile, RETURNCODE_FILEINTENT);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Re-Check that Service is running. In some rare cases, this may not be the case.
        AndroidSingleton.onCreateMainActivity(this, guiBridge, null);
        AndroidSingleton.onMainActivityVisible(0);
    }

    @Override
    public void onStop() {
        super.onStop();
        // notify service about the gui becoming invisible.
        // service will stop itself after a while to preserve resources
        AndroidSingleton.onMainActivityHidden(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getBaseContext().unregisterReceiver(networkStateChangeReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // set up connectivity listener and trigger once to get the current status
        getBaseContext().registerReceiver(networkStateChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        networkStateChangeReceiver.onReceive(getBaseContext(), null);
        // returning from settings?
        if (launchedSettings) {
            launchedSettings = false;
            guiListener.reloadConfiguration();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.lxc_layout, menu);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.quit:
            if (guiListener.shutdown(false, true, false)) {
                AndroidSingleton.onRealDestroy();
                finish();
            }
            return true;
        case R.id.settings:
            // display settings
            Intent showSettings = new Intent();
            showSettings.setClass(getBaseContext(), SettingsActivity.class);
            launchedSettings = true;
            startActivity(showSettings);
            return true;
        case R.id.help:
            // display help
            Intent showHelp = new Intent();
            showHelp.setClass(getBaseContext(), HelpActivity.class);
            startActivity(showHelp);
            return true;
        case R.id.about:
            // display about
            Intent showAbout = new Intent();
            showAbout.setClass(getBaseContext(), AboutActivity.class);
            startActivity(showAbout);
            return true;
        case R.id.pcversion:
            // display info about pc version
            Intent showPCVersion = new Intent();
            showPCVersion.setClass(getBaseContext(), PCVersionActivity.class);
            startActivity(showPCVersion);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            // User pressed "back"/"cancel" etc
            return;
        }

        ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
        // multiple files
        if (android.os.Build.VERSION.SDK_INT >= 18 && data.getClipData() != null) {
            List<VirtualFile> virtualFiles = virtualFilesFromClipData(data.getClipData());
            if (virtualFiles != null && !virtualFiles.isEmpty()) {
                files.addAll(virtualFiles);
            } else {
                handleShareError(data);
            }
        } else if (data.getData() != null) {
            VirtualFile virtualFile = uriToVirtualFile(data.getData());
            if (virtualFile != null) {
                files.add(virtualFile);
            } else {
                handleShareError(data);
            }
        }

        if (!files.isEmpty()) {
            offerFiles(files);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case Intent.ACTION_MAIN:
                return;
            case ACTION_STOP_FROMSERVICE:
                // try shutdown, but do not ask for transfers yet (the dialog will never show because this activity may
                // currently be invisible
                if (guiListener.shutdown(false, false, false)) {
                    // no transfers running, shutdown went through
                    AndroidSingleton.onRealDestroy();
                    finish();
                } else {
                    // transfers running, need to prompt user
                    // for this, the activity has to be visible - this means this method must return
                    // re-fire intent
                    Intent quitIntent = new Intent(this, MainActivity.class);
                    quitIntent.setAction(MainActivity.ACTION_STOP_FROMACTIVITY);
                    startActivity(quitIntent);
                }
                break;
            case ACTION_STOP_FROMACTIVITY:
                if (guiListener.shutdown(false, true, false)) {
                    // no transfers running, shutdown went through
                    AndroidSingleton.onRealDestroy();
                    finish();
                }
                break;
            case Intent.ACTION_SEND:
            case Intent.ACTION_SEND_MULTIPLE:
                // share
                List<VirtualFile> files = computeInputIntent(intent);
                if (files != null && !files.isEmpty()) {
                    if (guiListener == null) {
                        // service not ready yet, cache
                        AndroidSingleton.onEarlyShareIntent(files);
                    } else {
                        permissionPromptQuickshare = files;
                        if (PermissionTools.verifyStoragePermission(this, this)) {
                            offerFiles(files);
                            permissionPromptQuickshare = null;
                        }// otherwise prompt is going up, flow continues in callback
                    }
                } else {
                    handleShareError(intent);
                }
                break;
            case ACTION_SHOW_ERROR:
                showErrorDialog(this, intent.getCharSequenceExtra(Intent.EXTRA_TEXT));
                break;
            default:
                logger.warn("Received unknown intent! " + intent.getAction());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == RETURNCODE_PERMISSION_PROMPT_STORAGE) {
            if (grantResults.length == 0) {
                // cancelled, try again
                PermissionTools.verifyStoragePermission(this, this);
                return;
            }
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // continue what the user tried to do when the permission dialog fired
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (permissionPromptQuickshare != null) {
                            offerFiles(permissionPromptQuickshare);
                            permissionPromptQuickshare = null;
                        } else if (permissionPromptDownloadFile != null) {
                            guiListener.downloadFile(permissionPromptDownloadFile, false);
                            permissionPromptDownloadFile = null;
                        } else if (permissionPromptShareFile) {
                            shareFile();
                            permissionPromptShareFile = false;
                        }
                    }
                });
                t.setName("lxc_helper_useraction");
                t.setDaemon(true);
                t.start();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                cancelPermissionPromptAction();
            }
        }
    }

    /**
     * Cancels the action that resulted in a permission prompt going up
     */
    public void cancelPermissionPromptAction() {
        // cancel action and tell user
        if (permissionPromptDownloadFile != null) {
            permissionPromptDownloadFile.setLocked(false);
            fileListView.updateGui();
        }
        permissionPromptDownloadFile = null;
        permissionPromptQuickshare = null;
        permissionPromptShareFile = false;
        Snackbar.make(findViewById(android.R.id.content), R.string.snackbar_action_cancelled_permission_storage_missing, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Called when importing content to share failed.
     * Logs the Intent for debug purposes and displays a Toast.
     *
     */
    private void handleShareError(final Intent failedIntent) {
        logger.error("Sharing failed. Intent details:");
        logger.error("Intent object: " + failedIntent);
        logger.error("Intent action: " + failedIntent.getAction());
        logger.error("Intent dataString: " + failedIntent.getDataString());
        logger.error("Intent data: " + failedIntent.getData());
        logger.error("Intent type: " + failedIntent.getType());
        logger.error("Intent scheme: " + failedIntent.getScheme());
        logger.error("Intent package: " + failedIntent.getPackage());
        logger.error("Intent extras: " + failedIntent.getExtras());
        logger.error("Intent clipData: " + failedIntent.getClipData());

        AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.main_layout).getContext());
        builder.setTitle(R.string.error_cantoffer_title);
        builder.setMessage(R.string.error_cantoffer_text);
        builder.setPositiveButton(R.string.error_cantoffer_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });
        builder.setNeutralButton(R.string.error_cantoffer_mail, new DialogInterface.OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(DialogInterface dialog, int which) {
                 String uriText = "mailto:" + Uri.encode("mail@lanxchange.com") +
                        "?subject=" + Uri.encode("Cannot share file") +
                        "&body=" + Uri.encode("Hi!\n\nSharing some files failed :(\nPlease help!\n(feel free to write more)"
                        + "\n---------------------------------------"
                        + "\ntechnical info (do not remove this): "
                        + "\n---------------------------------------"
                        + "\n" + failedIntent
                        + "\n" + failedIntent.getAction()
                        + "\n" + failedIntent.getDataString()
                        + "\n" + failedIntent.getData()
                        + "\n" + failedIntent.getType()
                        + "\n" + failedIntent.getScheme()
                        + "\n" + failedIntent.getPackage()
                        + "\n" + failedIntent.getExtras()
                        + "\n" + failedIntent.getClipData());
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(uriText)));
            }
        });
        builder.show();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private List<VirtualFile> virtualFilesFromClipData(ClipData clipdata) {
        ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
        for (int i = 0; i < clipdata.getItemCount(); i++) {
            ClipData.Item item = clipdata.getItemAt(i);
            // may contain Uri or String
            if (item.getUri() != null) {
                VirtualFile file = uriToVirtualFile(item.getUri());
                if (file != null) {
                    result.add(file);
                }
            } else if (item.getText() != null) {
                // plain text
                try {
                    ByteArrayOutputStream arrayOutput = new ByteArrayOutputStream();
                    OutputStreamWriter writer = new OutputStreamWriter(arrayOutput);
                    writer.write(item.getText().toString());
                    writer.close();
                    result.add(new InMemoryFile("text.txt", arrayOutput.toByteArray()));
                } catch (IOException ex) {
                    logger.error("Unable to create InMemoryFile from clipdata", ex);
                }
            }
        }
        return result;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private List<VirtualFile> computeInputIntent(Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            // use ClipData if available (newer api)
            ClipData clip = intent.getClipData();
            if (clip != null) {
                return virtualFilesFromClipData(clip);
            } else {
                // no clip, try extra stream
                Object data = intent.getExtras().get(Intent.EXTRA_STREAM);
                if (data != null && (data.toString().startsWith("file://") || data.toString().startsWith("content:"))) {
                    // Make file available asap:
                    ArrayList<Uri> uris = new ArrayList<Uri>();
                    uris.add(Uri.parse(intent.getExtras().get(Intent.EXTRA_STREAM).toString()));
                    return urisToVirtualFiles(uris);
                }
            }
        } else if (intent.getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
            // there is a legacy and a new way to receive multiple files
            // try the new first
            if (intent.getClipData() != null) {
                return virtualFilesFromClipData(intent.getClipData());
            } else if (intent.getStringArrayListExtra(Intent.EXTRA_STREAM) != null) {
                ArrayList<Uri> uris = new ArrayList<Uri>();
                @SuppressWarnings("rawtypes")
                ArrayList uriStrings = intent.getStringArrayListExtra(Intent.EXTRA_STREAM);
                for (Object uriString : uriStrings) {
                    uris.add(Uri.parse(uriString.toString()));
                }
                return urisToVirtualFiles(uris);
            }
        }
        return null;
    }

    private void setWifiWarning(boolean displayWarning) {
        findViewById(R.id.noWifiWarning).setVisibility(displayWarning ? View.VISIBLE : View.GONE);
    }

    /**
     * Offers files.
     * 
     * @param files the files to offer
     */
    private void offerFiles(List<VirtualFile> files) {
        if (files.isEmpty()) {
            logger.error("invalid input!");
            return;
        }

        LXCFile lxcfile = new LXCFile(files, files.get(0).getName());
        guiListener.offerFile(lxcfile);
    }

    private List<VirtualFile> urisToVirtualFiles(List<Uri> uris) {
        List<VirtualFile> list = new ArrayList<VirtualFile>();
        for (Uri uri : uris) {
            VirtualFile virtualFile = uriToVirtualFile(uri);
            if (virtualFile != null) {
                list.add(virtualFile);
            }
        }
        return list;
    }

    private VirtualFile uriToVirtualFile(Uri uri) {
        String uriString = uri.toString();
        VirtualFile file = null;
        // Handle kitkat files
        if (uriString.startsWith("content://")) {
            ContentResolver resolver = getBaseContext().getContentResolver();
            // get file name
            String[] projection = { MediaStore.Files.FileColumns.DISPLAY_NAME };
            Cursor cursor = resolver.query(uri, projection, null, null, null);
            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                String name = cursor.getString(column_index);
                try {
                    ParcelFileDescriptor desc = resolver.openFileDescriptor(uri, "r");
                    file = new NonFileContent(name, desc, uri, resolver);
                } catch (FileNotFoundException ex) {
                    logger.error("Unable to open fd and create NonFileContent", ex);
                }
                cursor.close();
            }
        } else if (uriString.startsWith("file://")) {
            // seems to be useable right away
            file = new RealFile(new File(uri.getPath()));
        }
        return file;
    }

    private void promptDownloadTarget(LXCFile file) {
        // create dialog/fragment here
        final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                .allowReadOnlyDirectory(false)
                .newDirectoryName("LanXchange")
                .allowNewDirectoryNameModification(true)
                .initialDirectory(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .getString("pref_downloadPath",
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        .getAbsolutePath()))
                .build();
        final DirectoryChooserFragment dialog = DirectoryChooserFragment.newInstance(config);
        dialog.show(getFragmentManager(), null);
        dialog.setDirectoryChooserListener(new DirectoryChooserFragment.OnFragmentInteractionListener() {

            @Override
            public void onSelectDirectory(@NonNull String path) {
                dialog.dismiss();
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        guiListener.downloadFile(file, new File(path));
                    }
                });
                t.setName("lxc_helper_initdl_" + file.getShownName());
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void onCancelChooser() {
                dialog.dismiss();
                file.setLocked(false);
                fileListView.updateGui();
            }
        });
    }

    /**
     * Sets the GuiListener. Will be called by AndroidSingleton when LXC is
     * ready. If this Activity has been recreated and LXC is still running,
     * AndroidSingleton calls this within onCreateMainActivity
     * 
     * @param guiListener
     *            out future GuiListener
     */
    public void setGuiListener(final AndroidGuiListener guiListener) {
        this.guiListener = guiListener;
        // special gui listener for filelistview, catches download call to check permissions first
        fileListView.setGuiListener(new AndroidGuiListener(guiListener) {
            @Override
            public void guiHidden(int depth) {
                guiListener.guiHidden(depth);
            }

            @Override
            public void guiVisible(int depth) {
                guiListener.guiVisible(depth);
            }

            @Override
            public void downloadFile(LXCFile file, boolean chooseTarget) {
                permissionPromptDownloadFile = file;
                if (PermissionTools.verifyStoragePermission(MainActivity.this, MainActivity.this)) {
                    permissionPromptDownloadFile = null;
                    // prompt for download target?
                    if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("pref_askDownloadTarget", false)) {
                        promptDownloadTarget(file);
                    } else {
                        // use default dl target
                        guiListener.downloadFile(file, chooseTarget);
                    }
                } // otherwise prompt is going up, flow continues in callback
            }
        });
        fileListView.updateGui();
    }

    /**
     * When this activity is started with an ACTION_SEND Intent, the path of the
     * file to share will end up here.
     * 
     * @param uris a list of Uris to share
     */
    public void quickShare(List<VirtualFile> uris) {
        permissionPromptQuickshare = uris;
        if (PermissionTools.verifyStoragePermission(this, this)) {
            offerFiles(uris);
            permissionPromptQuickshare = null;
        } // otherwise prompt is going up, flow continues in callback
    }

    /**
     * Used by AndroidSingleton to copy some error codes from LXCService.
     */
    public void onErrorCode(int errorCode) {
        switch (errorCode) {
            case 1:
                AlertDialog.Builder builder = new AlertDialog.Builder(findViewById(R.id.main_layout).getContext());
                builder.setTitle(R.string.error_sdcard_title);
                builder.setMessage(R.string.error_sdcard_text);
                builder.setCancelable(false);
                builder.setPositiveButton("OK", new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        // critical, force exit
                        guiListener.shutdown(true, false, false);
                        AndroidSingleton.onRealDestroy();
                        finish();
                    }
                });
                builder.show();
                break;
        }
    }
}
