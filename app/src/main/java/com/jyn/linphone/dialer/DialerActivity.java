package com.jyn.linphone.dialer;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.jyn.linphone.LinphoneManager;
import com.jyn.linphone.R;
import com.jyn.linphone.activities.MainActivity;
import com.jyn.linphone.call.views.CallButton;
import com.jyn.linphone.dialer.views.AddressText;
import com.jyn.linphone.dialer.views.EraseButton;
import com.jyn.linphone.utils.LinphoneUtils;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;

public class DialerActivity extends MainActivity implements AddressText.AddressChangedListener {
    private AddressText mAddress;
    private CallButton mStartCall;
    private CoreListenerStub mListener;
    private ImageView mStatusLed;
    private TextView mStatusText;
    private TextView displayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialer);
        initUI();
        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        updateLayout();
                    }

                    @Override
                    public void onRegistrationStateChanged(
                            final Core core,
                            final ProxyConfig proxy,
                            final RegistrationState state,
                            String smessage) {
                        if (core.getProxyConfigList() == null) {
                            return;
                        }
                        if ((core.getDefaultProxyConfig() != null
                                        && core.getDefaultProxyConfig().equals(proxy))
                                || core.getDefaultProxyConfig() == null) {
                            mStatusLed.setImageResource(getStatusIconResource(state));
                            mStatusText.setText(getStatusIconText(state));
                        }

                        try {
                            mStatusText.setOnClickListener(
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Core core = LinphoneManager.getCore();
                                            if (core != null) {
                                                core.refreshRegisters();
                                            }
                                        }
                                    });
                        } catch (IllegalStateException ise) {
                            Log.e(ise);
                        }
                    }
                };
        mPermissionsToHave =
                new String[] {
                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                    "android.permission.FOREGROUND_SERVICE",
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.READ_CONTACTS
                };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
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
        mAddress = null;
        mStartCall = null;
        if (mListener != null) mListener = null;
        super.onDestroy();
    }

    private void initUI() {
        mAddress = findViewById(R.id.address);
        mAddress.setAddressListener(this);
        EraseButton erase = findViewById(R.id.erase);
        erase.setAddressWidget(mAddress);
        mStartCall = findViewById(R.id.start_call);
        mStartCall.setAddressWidget(mAddress);
        if (getIntent() != null) {
            mAddress.setText(getIntent().getStringExtra("SipUri"));
        }
        mStatusLed = findViewById(R.id.status_led);
        mStatusText = findViewById(R.id.status_text);
        displayName = findViewById(R.id.tv_current_account_name);
        updateLayout();
    }

    private void updateLayout() {
        Core core = LinphoneManager.getCore();
        if (core == null) {
            return;
        }
        boolean atLeastOneCall = core.getCallsNb() > 0;
        mStartCall.setVisibility(atLeastOneCall ? View.GONE : View.VISIBLE);
        if (!atLeastOneCall) {
            if (core.getVideoActivationPolicy().getAutomaticallyInitiate()) {
                mStartCall.setImageResource(R.drawable.call_video_start);
            } else {
                mStartCall.setImageResource(R.drawable.call_audio_start);
            }
        }
        showAccountInfo();
    }

    /** 显示当前用户姓名 */
    public void showAccountInfo() {
        ProxyConfig proxy = LinphoneManager.getCore().getDefaultProxyConfig();
        if (proxy == null) {
            return;
        }
        displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getIdentityAddress()));
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            if (state == RegistrationState.Ok) {
                return R.drawable.led_connected;
            } else if (state == RegistrationState.Progress) {
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.Failed) {
                return R.drawable.led_error;
            } else {
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return R.drawable.led_disconnected;
    }

    private String getStatusIconText(RegistrationState state) {
        try {
            if (state == RegistrationState.Ok) {
                return getString(R.string.status_connected);
            } else if (state == RegistrationState.Progress) {
                return getString(R.string.status_in_progress);
            } else if (state == RegistrationState.Failed) {
                return getString(R.string.status_error);
            } else {
                return getString(R.string.status_not_connected);
            }
        } catch (Exception e) {
            Log.e(e);
        }
        return getString(R.string.status_not_connected);
    }

    @Override
    public void onAddressChanged() {}
}
