/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.servermanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.server.common.Caller;
import io.github.muntashirakon.AppManager.server.common.CallerResult;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class LocalServer {
    private static final String LOG_FILE = "am.log";

    @SuppressLint("StaticFieldLeak")
    private static LocalServer INSTANCE;

    public static LocalServer getInstance() {
        if (INSTANCE == null) {
            synchronized (LocalServer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocalServer();
                }
            }
        }
        return INSTANCE;
    }

    private LocalServerManager mLocalServerManager;
    private Context mContext;

    private LocalServer() {
        mContext = AppManager.getContext();
        Config config = new Config();
        config.context = mContext;
        updateConfig(config);
        int userHandleId = Users.getCurrentUserHandle();
        ServerConfig.init(config.context, userHandleId);
        mLocalServerManager = LocalServerManager.getInstance(config);
        // Check if am.jar is in the right place
        checkFile();
        // Start server if not already
        try {
            checkConnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    Config getConfig() {
        return mLocalServerManager.getConfig();
    }

    Context getContext() {
        return mContext;
    }

    LocalServerManager getLocalServerManager() {
        return mLocalServerManager;
    }

    public synchronized void checkConnect() throws Exception {
        mLocalServerManager.start();
    }

    public CallerResult exec(Caller caller) throws Exception {
        checkConnect();
        return mLocalServerManager.execNew(caller);
    }

    public boolean isRunning() {
        return mLocalServerManager != null && mLocalServerManager.isRunning();
    }

    public void destroy() {
        if (mLocalServerManager != null) {
            mLocalServerManager.stop();
        }
    }

    public void closeBgServer() {
        if (mLocalServerManager != null) {
            mLocalServerManager.closeBgServer();
            mLocalServerManager.stop();
        }
    }

    private void checkFile() {
        AssetsUtils.copyFile(mContext, ServerConfig.JAR_NAME, ServerConfig.getDestJarFile(), BuildConfig.DEBUG);
        AssetsUtils.writeScript(getConfig());
    }

    public static void updateConfig() {
        if (INSTANCE != null) {
            Config config = INSTANCE.getConfig();
            if (config != null) {
                updateConfig(config);
            }
        }
    }

    private static void updateConfig(@NonNull Config config) {
        // FIXME: Use AppPref instead of SharedPreferences
        // FIXME(10/9/20): These prefs are not saved anywhere
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(config.context);
        config.allowBgRunning = sp.getBoolean("allow_bg_running", true);
        config.logFile = config.context.getFileStreamPath(LOG_FILE).getAbsolutePath();
        config.useAdb = AppPref.isAdbEnabled() && !AppPref.isRootEnabled();
        config.adbPort = sp.getInt("adb_port", 5555);
        config.rootOverAdb = sp.getBoolean("allow_root_over_adb", false);
        Log.e("test", "buildConfig --> " + config.context.getFileStreamPath(LOG_FILE).getAbsolutePath());
        if (INSTANCE != null) INSTANCE.mLocalServerManager.updateConfig(config);
    }

    @NonNull
    public static String readLogs(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELinux:");
        if (AppOpsManager.isEnableSELinux()) {
            sb.append("Enforcing");
        }
        sb.append("\n\n");

        File file = context.getFileStreamPath(LOG_FILE);
        if (file.exists()) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(file));
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    sb.append("\n");
                    line = br.readLine();
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (br != null) {
                        br.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else sb.append("No logs");
        return sb.toString();
    }

    @Nullable
    private static String readProcess() {
        Process exec = null;
        BufferedReader br = null;
        try {
            exec = Runtime.getRuntime().exec("su -C 'ps'");
            br = new BufferedReader(new InputStreamReader(exec.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                Log.e("test", "readProcess --> " + line);
                //sb.append(line);
                //sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (exec != null) {
                exec.destroy();
            }
        }
        return null;
    }

    public static class Config {
        public boolean allowBgRunning = false;
        public String logFile;
        public boolean printLog = false;
        public boolean useAdb = AppPref.isAdbEnabled() && !AppPref.isRootEnabled();
        public boolean rootOverAdb = false;
        public String adbHost = "127.0.0.1";
        public int adbPort = 5555;
        Context context;
    }
}
