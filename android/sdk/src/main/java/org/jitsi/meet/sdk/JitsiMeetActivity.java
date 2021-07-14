/*
 * Copyright @ 2019-present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.meet.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.modules.core.PermissionListener;

import org.jitsi.meet.sdk.log.JitsiMeetLogger;

import java.util.HashMap;
import android.app.Activity;
import android.util.Log;

/**
 * A base activity for SDK users to embed. It uses {@link JitsiMeetFragment} to do the heavy
 * lifting and wires the remaining Activity lifecycle methods so it works out of the box.
 */
public class JitsiMeetActivity extends FragmentActivity
    implements JitsiMeetActivityInterface {

    protected static final String TAG = JitsiMeetActivity.class.getSimpleName();

    private static final String ACTION_JITSI_MEET_CONFERENCE = "org.jitsi.meet.CONFERENCE";
    private static final String JITSI_MEET_CONFERENCE_OPTIONS = "JitsiMeetConferenceOptions";

    public static boolean isSessionActive = false;
    protected static Context currentCallingContext;
    protected static FragmentActivity thisActivity;
    private static int LAUNCH_FLAG = Intent.FLAG_ACTIVITY_NEW_TASK;
    private static int LAUNCH_FLAG_APP = Intent.FLAG_ACTIVITY_NEW_TASK;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onBroadcastReceived(intent);
        }
    };

    public static int getLaunchFlagApp() {
        return LAUNCH_FLAG_APP;
    }

    public static void setLaunchFlagApp(int launchFlagApp) {
        LAUNCH_FLAG_APP = launchFlagApp;
    }

    public static boolean isIsSessionActive() {
        return isSessionActive;
    }

    public static void setIsSessionActive(boolean isSessionActive) {
        JitsiMeetActivity.isSessionActive = isSessionActive;
    }

    public static FragmentActivity getThisActivity() {
        return thisActivity;
    }

    public static void setThisActivity(FragmentActivity thisActivity) {
        JitsiMeetActivity.thisActivity = thisActivity;
    }

    // Helpers for starting the activity

    public static void setLaunchFlag(int launchFlag) {
        LAUNCH_FLAG = launchFlag;
    }

    public static void launch(Context context, JitsiMeetConferenceOptions options) {
        if (!isIsSessionActive()) {
            return;
        }
        Intent intent = new Intent(context, JitsiMeetActivity.class);
        intent.setAction(ACTION_JITSI_MEET_CONFERENCE);
        if (LAUNCH_FLAG != -1)
            intent.setFlags(LAUNCH_FLAG);
        intent.putExtra(JITSI_MEET_CONFERENCE_OPTIONS, options);
        //if block is new
        if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        setCurrentCallingContext(context);
        getCurrentCallingContext().startActivity(intent);
    }

    public static void launch(Context context, String url) {
        JitsiMeetConferenceOptions options
            = new JitsiMeetConferenceOptions.Builder().setRoom(url).build();
        setCurrentCallingContext(context);
        launch(getCurrentCallingContext(), options);
    }

    public static void launchCurrentJitsiCall(Context context) {
        //@cobrowsing log launchCurrentJitsiCall
        JitsiMeetLogger.d(TAG + " cobrowsing-launchCurrentJitsiCall: context " + context);
        try {
            setCurrentCallingContext(context);
            Intent intent = new Intent(context, JitsiMeetActivity.class);
            if (LAUNCH_FLAG != -1)
                intent.setFlags(LAUNCH_FLAG);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Context getCurrentCallingContext() {
        return currentCallingContext;
    }

    public static void setCurrentCallingContext(Context currentCallingContext) {
        JitsiMeetActivity.currentCallingContext = currentCallingContext;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
        Log.d(TAG, "onResume: Current Jitsi View " + getJitsiView());
        thisActivity = this;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart() called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called");
        Log.d(TAG, "onResume: Current Jitsi View " + getJitsiView());
        if (!isIsSessionActive()) {
            this.finish();
        }
        thisActivity = this;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_jitsi_meet);

        registerForBroadcastMessages();

        if (!extraInitialize()) {
            initialize();
        }
    }

    @Override
    public void onDestroy() {
        // Here we are trying to handle the following corner case: an application using the SDK
        // is using this Activity for displaying meetings, but there is another "main" Activity
        // with other content. If this Activity is "swiped out" from the recent list we will get
        // Activity#onDestroy() called without warning. At this point we can try to leave the
        // current meeting, but when our view is detached from React the JS <-> Native bridge won't
        // be operational so the external API won't be able to notify the native side that the
        // conference terminated. Thus, try our best to clean up.

        JitsiMeetLogger.d(TAG + " cobrowsing-onDestroy: checkForWhoFirst");
        thisActivity = null;
        setCurrentCallingContext(null);

        leave();
        if (AudioModeModule.useConnectionService()) {
            ConnectionService.abortConnections();
        }
        JitsiMeetOngoingConferenceService.abort(this);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

        super.onDestroy();
    }

    @Override
    public void finish() {
        leave();
        // cobrowsing custom changes
        if (AudioModeModule.useConnectionService()) {
            //@cobrowsing log finish
            JitsiMeetLogger.d(TAG + " cobrowsing-finish: useConnectionService");
            ConnectionService.abortConnections();
        }

        super.finish();
    }

    // cobrowsing custom changes
    @Override
    public void finishAndRemoveTask() {
        JitsiMeetLogger.d(TAG + " cobrowsing-finishAndRemoveTask: ");
        leave();
        if (AudioModeModule.useConnectionService()) {
            //@cobrowsing log finish
            JitsiMeetLogger.d(TAG + " cobrowsing-finishAndRemoveTask: useConnectionService");
            ConnectionService.abortConnections();
        }
        JitsiMeetOngoingConferenceService.abort(this);
        super.finishAndRemoveTask();
    }

    // Helper methods
    //

    protected JitsiMeetView getJitsiView() {
        JitsiMeetFragment fragment
            = (JitsiMeetFragment) getSupportFragmentManager().findFragmentById(R.id.jitsiFragment);
        return fragment != null ? fragment.getJitsiView() : null;
    }

    public void join(@Nullable String url) {
        JitsiMeetConferenceOptions options
            = new JitsiMeetConferenceOptions.Builder()
            .setRoom(url)
            .build();
        join(options);
    }

    public void join(JitsiMeetConferenceOptions options) {
        JitsiMeetView view = getJitsiView();

        if (view != null) {
            view.join(options);
        } else {
            JitsiMeetLogger.w("Cannot join, view is null");
        }
    }

    public void leave() {
        try {
            JitsiMeetView view = getJitsiView();

            if (view != null) {
                view.leave();
            } else {
                JitsiMeetLogger.w("Cannot leave, view is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private @Nullable
    JitsiMeetConferenceOptions getConferenceOptions(Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                return new JitsiMeetConferenceOptions.Builder().setRoom(uri.toString()).build();
            }
        } else if (ACTION_JITSI_MEET_CONFERENCE.equals(action)) {
            return intent.getParcelableExtra(JITSI_MEET_CONFERENCE_OPTIONS);
        }

        return null;
    }

    /**
     * Helper function called during activity initialization. If {@code true} is returned, the
     * initialization is delayed and the {@link JitsiMeetActivity#initialize()} method is not
     * called. In this case, it's up to the subclass to call the initialize method when ready.
     * <p>
     * This is mainly required so we do some extra initialization in the Jitsi Meet app.
     *
     * @return {@code true} if the initialization will be delayed, {@code false} otherwise.
     */
    protected boolean extraInitialize() {
        return false;
    }

    protected void initialize() {
        // Join the room specified by the URL the app was launched with.
        // Joining without the room option displays the welcome page.
        join(getConferenceOptions(getIntent()));
    }

    protected void onConferenceJoined(HashMap<String, Object> extraData) {
        JitsiMeetLogger.i("Conference joined: " + extraData);
        // Launch the service for the ongoing notification.
        JitsiMeetOngoingConferenceService.launch(this);
    }

    protected void onConferenceTerminated(HashMap<String, Object> extraData) {
        JitsiMeetLogger.i("Conference terminated: " + extraData);
        thisActivity = null;
        launchCallingActivity();
        finish();
    }

    protected void onConferenceWillJoin(HashMap<String, Object> extraData) {
        JitsiMeetLogger.i("Conference will join: " + extraData);
    }

    protected void onParticipantJoined(HashMap<String, Object> extraData) {
        try {
            JitsiMeetLogger.i("Participant joined: ", extraData);
        } catch (Exception e) {
            JitsiMeetLogger.w("Invalid participant joined extraData", e);
        }
    }

    protected void onParticipantLeft(HashMap<String, Object> extraData) {
        try {
            JitsiMeetLogger.i("Participant left: ", extraData);
        } catch (Exception e) {
            JitsiMeetLogger.w("Invalid participant left extraData", e);
        }
    }

    // Activity lifecycle methods
    //

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        JitsiMeetActivityDelegate.onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
//        JitsiMeetActivityDelegate.onBackPressed();
        Intent intent = new Intent(JitsiMeetActivity.this, getCurrentCallingContext().getClass());
        startActivity(intent);
    }

    private void launchCallingActivity() {
        Intent intent = new Intent(JitsiMeetActivity.this, getCurrentCallingContext().getClass());
        intent.addFlags(LAUNCH_FLAG_APP);
        startActivity(intent);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        JitsiMeetConferenceOptions options;

        if ((options = getConferenceOptions(intent)) != null) {
            join(options);
            return;
        }

        JitsiMeetActivityDelegate.onNewIntent(intent);
    }

    @Override
    protected void onUserLeaveHint() {
//        JitsiMeetView view = getJitsiView();
//
//        if (view != null) {
//            view.enterPictureInPicture();
//        }
    }

    // JitsiMeetActivityInterface
    //

    @Override
    public void requestPermissions(String[] permissions, int requestCode, PermissionListener listener) {
        JitsiMeetActivityDelegate.requestPermissions(this, permissions, requestCode, listener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        JitsiMeetActivityDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void registerForBroadcastMessages() {
        IntentFilter intentFilter = new IntentFilter();

        for (BroadcastEvent.Type type : BroadcastEvent.Type.values()) {
            intentFilter.addAction(type.getAction());
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    private void onBroadcastReceived(Intent intent) {
        if (intent != null) {
            BroadcastEvent event = new BroadcastEvent(intent);

            switch (event.getType()) {
                case CONFERENCE_JOINED:
                    onConferenceJoined(event.getData());
                    break;
                case CONFERENCE_WILL_JOIN:
                    onConferenceWillJoin(event.getData());
                    break;
                case CONFERENCE_TERMINATED:
                    onConferenceTerminated(event.getData());
                    break;
                case PARTICIPANT_JOINED:
                    onParticipantJoined(event.getData());
                    break;
                case PARTICIPANT_LEFT:
                    onParticipantLeft(event.getData());
                    break;
            }
        }
    }
}
