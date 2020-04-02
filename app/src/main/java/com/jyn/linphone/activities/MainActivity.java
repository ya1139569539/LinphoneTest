package com.jyn.linphone.activities;

import android.Manifest;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import androidx.core.app.ActivityCompat;
import com.jyn.linphone.LinphoneContext;
import com.jyn.linphone.LinphoneManager;
import com.jyn.linphone.R;
import com.jyn.linphone.call.CallActivity;
import com.jyn.linphone.call.CallIncomingActivity;
import com.jyn.linphone.call.CallOutgoingActivity;
import com.jyn.linphone.compatibility.Compatibility;
import com.jyn.linphone.dialer.DialerActivity;
import com.jyn.linphone.settings.LinphonePreferences;
import com.jyn.linphone.utils.DeviceUtils;
import com.jyn.linphone.utils.LinphoneUtils;
import java.util.ArrayList;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;

public abstract class MainActivity extends LinphoneGenericActivity {
    private static final int MAIN_PERMISSIONS = 1;
    protected static final int FRAGMENT_SPECIFIC_PERMISSION = 2;

    protected boolean mOnBackPressGoHome;
    protected boolean mAlwaysHideTabBar;
    protected String[] mPermissionsToHave;

    private CoreListenerStub mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOnBackPressGoHome = true;
        mAlwaysHideTabBar = false;
        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Released) {
                            displayMissedCalls();
                        }
                    }

                    @Override
                    public void onMessageReceived(Core core, ChatRoom room, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onChatRoomRead(Core core, ChatRoom room) {
                        displayMissedChats();
                    }

                    @Override
                    public void onMessageReceivedUnableDecrypt(
                            Core core, ChatRoom room, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onRegistrationStateChanged(
                            Core core,
                            ProxyConfig proxyConfig,
                            RegistrationState state,
                            String message) {
                        if (state == RegistrationState.Ok) {
                            DeviceUtils
                                    .displayDialogIfDeviceHasPowerManagerThatCouldPreventPushNotifications(
                                            MainActivity.this);
                            if (getResources().getBoolean(R.bool.use_phone_number_validation)) {
                                AuthInfo authInfo =
                                        core.findAuthInfo(
                                                proxyConfig.getRealm(),
                                                proxyConfig.getIdentityAddress().getUsername(),
                                                proxyConfig.getDomain());
                                if (authInfo != null
                                        && authInfo.getDomain()
                                                .equals(getString(R.string.default_domain))) {
                                    LinphoneManager.getInstance().isAccountWithAlias();
                                }
                            }

                            if (!Compatibility.isDoNotDisturbSettingsAccessGranted(
                                    MainActivity.this)) {
                                displayDNDSettingsDialog();
                            }
                        }
                    }
                };
    }

    @Override
    protected void onStart() {
        super.onStart();

        requestRequiredPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LinphoneContext.instance()
                .getNotificationManager()
                .removeForegroundServiceNotificationIfPossible();

        hideTopBar();
        if (!mAlwaysHideTabBar
                && (getFragmentManager().getBackStackEntryCount() == 0
                        || !getResources()
                                .getBoolean(R.bool.hide_bottom_bar_on_second_level_views))) {
            showTabBar();
        }
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
            displayMissedChats();
            displayMissedCalls();
        }
    }

    @Override
    protected void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        mListener = null;

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
        } catch (IllegalStateException ise) {
            // Do not log this exception
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } catch (IllegalStateException ise) {
            // Do not log this exception
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mOnBackPressGoHome) {
                if (getFragmentManager().getBackStackEntryCount() == 0) {
                    goHomeAndClearStack();
                    return true;
                }
            }
            goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean popBackStack() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStackImmediate();
            if (!mAlwaysHideTabBar
                    && (getFragmentManager().getBackStackEntryCount() == 0
                            && getResources()
                                    .getBoolean(R.bool.hide_bottom_bar_on_second_level_views))) {
                showTabBar();
            }
            return true;
        }
        return false;
    }

    public void goBack() {
        finish();
    }

    protected boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    private void goHomeAndClearStack() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(intent);
        } catch (IllegalStateException ise) {
            Log.e("[Main Activity] Can't start home activity: ", ise);
        }
    }

    public void hideTabBar() {}

    public void showTabBar() {}

    protected void hideTopBar() {}

    private void showTopBar() {}

    protected void showTopBarWithTitle(String title) {
        showTopBar();
    }

    // Permissions

    public boolean checkPermission(String permission) {
        int granted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " permission is "
                        + (granted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissions(String[] permissions) {
        boolean allGranted = true;
        for (String permission : permissions) {
            allGranted &= checkPermission(permission);
        }
        return allGranted;
    }

    public void requestPermissionIfNotGranted(String permission) {
        if (!checkPermission(permission)) {
            String[] permissions = new String[] {permission};
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                ActivityCompat.requestPermissions(this, permissions, FRAGMENT_SPECIFIC_PERMISSION);
            }
        }
    }

    public void requestPermissionsIfNotGranted(String[] perms) {
        requestPermissionsIfNotGranted(perms, FRAGMENT_SPECIFIC_PERMISSION);
    }

    private void requestPermissionsIfNotGranted(String[] perms, int resultCode) {
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        if (perms != null) {
            for (String permissionToHave : perms) {
                if (!checkPermission(permissionToHave)) {
                    permissionsToAskFor.add(permissionToHave);
                }
            }
        }
        if (permissionsToAskFor.size() > 0) {
            String[] permissions = new String[permissionsToAskFor.size()];
            permissions = permissionsToAskFor.toArray(permissions);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                ActivityCompat.requestPermissions(this, permissions, resultCode);
            }
        }
    }

    private void requestRequiredPermissions() {
        requestPermissionsIfNotGranted(mPermissionsToHave, MAIN_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length <= 0) return;

        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                boolean enableRingtone = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
            } else if (permissions[i].equals(Manifest.permission.CAMERA)
                    && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                LinphoneUtils.reloadVideoDevices();
            }
        }
    }

    protected void displayMissedCalls() {}

    public void displayMissedChats() {}

    public void goBackToCall() {
        boolean incoming = false;
        boolean outgoing = false;
        Call[] calls = LinphoneManager.getCore().getCalls();
        for (Call call : calls) {
            Call.State state = call.getState();
            switch (state) {
                case IncomingEarlyMedia:
                case IncomingReceived:
                    incoming = true;
                    break;
                case OutgoingEarlyMedia:
                case OutgoingInit:
                case OutgoingProgress:
                case OutgoingRinging:
                    outgoing = true;
                    break;
            }
        }

        if (incoming) {
            startActivity(new Intent(this, CallIncomingActivity.class));
        } else if (outgoing) {
            startActivity(new Intent(this, CallOutgoingActivity.class));
        } else {
            startActivity(new Intent(this, CallActivity.class));
        }
    }

    public void newOutgoingCall(String to) {
        if (LinphoneManager.getCore().getCallsNb() > 0) {
            Intent intent = new Intent(this, DialerActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra("SipUri", to);
            this.startActivity(intent);
        } else {
            LinphoneManager.getCallManager().newOutgoingCall(to, null);
        }
    }

    protected void changeFragment(Fragment fragment, String name, boolean isChild) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (transaction.isAddToBackStackAllowed()) {
            int count = fragmentManager.getBackStackEntryCount();
            if (count > 0) {
                FragmentManager.BackStackEntry entry =
                        fragmentManager.getBackStackEntryAt(count - 1);

                if (entry != null && name.equals(entry.getName())) {
                    fragmentManager.popBackStack();
                    if (!isChild) {
                        // We just removed it's duplicate from the back stack
                        // And we want at least one in it
                        transaction.addToBackStack(name);
                    }
                }
            }

            if (isChild) {
                transaction.addToBackStack(name);
            }
        }

        if (getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views)) {
            if (isChild) {
                if (!isTablet()) {
                    hideTabBar();
                }
            } else {
                showTabBar();
            }
        }

        Compatibility.setFragmentTransactionReorderingAllowed(transaction, false);
        if (isChild && isTablet()) {
            transaction.replace(R.id.fragmentContainer2, fragment, name);
            findViewById(R.id.fragmentContainer2).setVisibility(View.VISIBLE);
        } else {
            transaction.replace(R.id.fragmentContainer, fragment, name);
        }
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
    }

    public Dialog displayDialog(String text) {
        return LinphoneUtils.getDialog(this, text);
    }

    private void displayDNDSettingsDialog() {
        if (!LinphonePreferences.instance().isDNDSettingsPopupEnabled()) return;
        Log.w("[Permission] Asking user to grant us permission to read DND settings");

        final Dialog dialog =
                displayDialog(getString(R.string.pref_grant_read_dnd_settings_permission_desc));
        dialog.findViewById(R.id.dialog_do_not_ask_again_layout).setVisibility(View.VISIBLE);
        final CheckBox doNotAskAgain = dialog.findViewById(R.id.doNotAskAgain);
        dialog.findViewById(R.id.doNotAskAgainLabel)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                doNotAskAgain.setChecked(!doNotAskAgain.isChecked());
                            }
                        });
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (doNotAskAgain.isChecked()) {
                            LinphonePreferences.instance().enableDNDSettingsPopup(false);
                        }
                        dialog.dismiss();
                    }
                });
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setVisibility(View.VISIBLE);
        ok.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(
                                    new Intent(
                                            "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"));
                        } catch (ActivityNotFoundException anfe) {
                            Log.e("[Main Activity] Activity not found exception: ", anfe);
                        }
                        dialog.dismiss();
                    }
                });
        Button delete = dialog.findViewById(R.id.dialog_delete_button);
        delete.setVisibility(View.GONE);
        dialog.show();
    }
}
