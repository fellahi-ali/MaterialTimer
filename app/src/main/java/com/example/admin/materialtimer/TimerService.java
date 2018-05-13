package com.example.admin.materialtimer;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;


/**
 * Created by admin on 4/21/18.
 */

public class TimerService extends Service implements TimerInterface {

    public enum Timer{
        Work, Break, LongBreak
    }

    private Timer timer;
    private Handler timerHandler;
    private HandlerThread workerThread;
    private long milliSecondsLeft,countDownInterval;
    private int sessionBeforeLongBreak,sessionCount;
    private boolean sessionStart,customFlag,connected,notification,running;
    private SharedPreferences sharedPref;
    private Runnable workRun,breakRun,longBreakRun;
    private CountDownTimer workTimer,breakTimer,longBreakTimer,customTimer;
    private NotificationUtil notifUtil;
    private Messenger timerMessenger, uiMessenger;

    private final String WORK_TIME = "pref_work_time";
    private final String BREAK_TIME = "pref_break_time";
    private final String LONG_BREAK_TIME = "pref_long_break_time";
    private final String LOOP_AMOUT_VALUE = "pref_loop_amount";

    public static final String TIMER_RESTART = "timer_service_restart";
    public static final String ACTION_START = "start";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESET = "reset";
    public static final int START_TIMER = 1;
    public static final int PAUSE_TIMER = 2;
    public static final int REGISTER_CLIENT = 3;
    public static final int SYNC_CLIENT = 4;
    public static final int START_NOTIFICATION = 5;
    public static final int STOP_NOTIFICATION = 6;

    /**
     * Handler for incoming messages from clients.
     */
    private class ServiceHandler extends Handler {

        public ServiceHandler(Looper looper){
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_TIMER:
                    startTimer();
                    break;
                case PAUSE_TIMER:
                    pauseTimer();
                    break;
                case REGISTER_CLIENT:
                    uiMessenger = msg.replyTo;
                    break;
                case SYNC_CLIENT:
                    synchronizeClient();
                    break;
                case START_NOTIFICATION:
                    startNotification();
                    break;
                case STOP_NOTIFICATION:
                    stopNotification();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }


    @Override
    public void onCreate(){
        super.onCreate();
        timer = Timer.Work;
        sessionStart = false;
        customFlag = false;
        connected = false;
        notification = false;
        running = false;
        sessionCount = 0;
        countDownInterval = 300;

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        notifUtil = new NotificationUtil(this);
        workerThread = new HandlerThread("WorkerThread", Process.THREAD_PRIORITY_BACKGROUND);
        workerThread.start();
        timerHandler = new Handler(workerThread.getLooper());
        timerMessenger = new Messenger(new ServiceHandler(workerThread.getLooper()));
        refreshTimers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        if(intent != null){
            if(intent.getAction() != null){
                switch(intent.getAction()){
                    case TIMER_RESTART:
                        startTimer();
                        break;
                    case ACTION_START:
                        startTimer();
                        startForeground(NotificationUtil.NOTIFICATION_ID,
                                notifUtil.buildNotification(formatTime(getTime()),
                                true));
                        notifUtil.updateNotification(formatTime(getTime()));
                        break;
                    case ACTION_PAUSE:
                        pauseTimer();
                        startForeground(NotificationUtil.NOTIFICATION_ID,
                                        notifUtil.buildNotification(formatTime(getTime()),
                                                false));
                        notifUtil.updateNotification(formatTime(getTime()));
                        break;
                    case ACTION_RESET:
                        resetTimer();
                        break;
                    default:
                        break;
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent){
        return timerMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent){
        uiMessenger = null;
        return true;
    }

    @Override
    public void onRebind(Intent intent){
        super.onRebind(intent);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.v("TimerService","onDestroy()");
    }

    @Override
    public void onTaskRemoved(Intent intent){
        /**
         * if timer is paused save state and restart in paused state
         * else if timer is running pause and restart
        */
        //if timer is running
        //save state & time
        pauseTimer();

        Intent restartIntent = new Intent(this, TimerReceiver.class);
        restartIntent.setAction(TIMER_RESTART);
        sendBroadcast(restartIntent);


        super.onTaskRemoved(intent);
        Log.v("TimerService","onTaskRemoved");
    }

    private void synchronizeClient(){
        //Update UI on initial startup && remove notification when returning to main UI
        if(timer == Timer.Work && !sessionStart){
            updateTimer(convertTime(sharedPref.getInt(WORK_TIME,25)));
        } else {
            if(notification){
                stopNotification();
                notification = false;
            }
            updateTimer(getTime());
        }

        Message msgState = Message.obtain();
        msgState.what = TimerActivity.UPDATE_STATE;
        msgState.obj = running;

        try{
            uiMessenger.send(msgState);
        } catch (RemoteException e){
            Log.d("TimerService", e.toString());
        }
        Log.v("TimerService","syncService");
    }

    private void startNotification(){
        if(!notification){
            startForeground(NotificationUtil.NOTIFICATION_ID, notifUtil.buildNotification(formatTime(getTime()),false));
            notifUtil.updateNotification(formatTime(getTime()));
            notification = true;
        }
        Log.v("startNotification","notification started");
    }

    private void stopNotification(){
        if(notification){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                stopForeground(NotificationUtil.NOTIFICATION_ID);
                notifUtil.hideTimer();
            } else {
                stopForeground(true);
                notifUtil.hideTimer();
            }
        }
    }

    public void saveTime(){
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong("timeLeft",milliSecondsLeft);
        editor.apply();
    }

    public long getTime(){
        return sharedPref.getLong("timeLeft",0);
    }

    public long convertTime(int value){
        return Long.valueOf(value) * 60000;
    }


    public void startTimer(){
        running = true;
        if(timer == Timer.Work && !sessionStart){
            sessionStart = true;
            timerHandler.post(workRun);
        } else {
            startCustomTimer(getTime());
        }
    }

    public void pauseTimer(){
        running = false;
        if(customFlag){
            saveTime();
            customTimer.cancel();
        } else {
            switch(timer){
                case Work:
                    saveTime();
                    workTimer.cancel();
                    break;
                case Break:
                    saveTime();
                    breakTimer.cancel();
                    break;
                case LongBreak:
                    saveTime();
                    longBreakTimer.cancel();
                    break;
                default:
                    break;
            }
        }
    }

    //TODO: stop dependent on timer state
    public void resetTimer(){
        if(connected){
            timer = Timer.Work;
            sessionStart = false;
            customFlag = false;
            refreshTimers();
            notifUtil.hideTimer();
        } else {
            timer = Timer.Work;
            sessionStart = false;
            customFlag = false;
            refreshTimers();
            notifUtil.hideTimer();
            workerThread.quit();
            stopSelf();
        }
    }

    public void startCustomTimer(long timeLeft){
        customFlag = true;
        customTimer = new CountDownTimer(timeLeft,countDownInterval) {
            @Override
            public void onTick(long millisUntilFinished) {
                milliSecondsLeft = millisUntilFinished;
                updateTimer(milliSecondsLeft);
            }

            @Override
            public void onFinish() {
                customFlag = false;
                switch (timer){
                    case Work:
                        if(sessionCount < sessionBeforeLongBreak){
                            sessionCount++;
                            timerHandler.post(breakRun);
                        } else {
                            sessionCount = 0;
                            timerHandler.post(longBreakRun);
                        }
                        break;
                    case Break:
                        timerHandler.post(workRun);
                        break;
                    case LongBreak:
                        timerHandler.post(workRun);
                        break;
                    default:
                        break;
                }

            }
        }.start();

    }

    public String formatTime(long milliSecondsLeft){
        int minutes = (int) milliSecondsLeft / 60000;
        int seconds = (int) milliSecondsLeft % 60000 / 1000;
        String currentTime = "";

        if (minutes < 10) {
            currentTime = "0" + minutes;
        } else {
            currentTime += minutes;
        }

        currentTime += ":";

        if (seconds < 10) {
            currentTime += "0" + seconds;
        } else {
            currentTime += seconds;
        }

        return currentTime;
    }

    public void updateTimer(long milliSecondsLeft){

        String currentTime = formatTime(milliSecondsLeft);

        if(notification){
            notifUtil.updateNotification(currentTime);
        } else {
            Message updateUI = Message.obtain();
            updateUI.what = TimerActivity.UPDATE_TIME;
            updateUI.obj = currentTime;
            try{
                uiMessenger.send(updateUI);
            } catch(RemoteException e){
                Log.e("RemoteException",e.toString());
            }
            Log.v("TimerService","updateTimer");
        }
    }

    public void refreshTimers(){
        //TODO: Get true values from shared preferences
//        final long workTime = 60000;
//        final long breakTime = 30000;
//        final long longBreakTime = 40000;

        final long workTime = convertTime(sharedPref.getInt(WORK_TIME,25));
        final long breakTime = convertTime(sharedPref.getInt(BREAK_TIME,5));
        final long longBreakTime = convertTime(sharedPref.getInt(LONG_BREAK_TIME,15));

        sessionBeforeLongBreak = sharedPref.getInt(LOOP_AMOUT_VALUE,4);

        //Issue with CountDownTimer implementation
        //CountDownInterval < 1000 as work around
        workRun = new Runnable(){
            @Override
            public void run(){
                timer = Timer.Work;
                workTimer = new CountDownTimer(workTime,countDownInterval) {
                    @Override
                    public void onTick(long l) {
                        milliSecondsLeft = l;
                        updateTimer(milliSecondsLeft);
                    }

                    @Override
                    public void onFinish() {
                        if(sessionCount < sessionBeforeLongBreak){
                            sessionCount++;
                            timerHandler.post(breakRun);
                        } else {
                            sessionCount = 0;
                            timerHandler.post(longBreakRun);
                        }
                    }
                }.start();
            }
        };

        breakRun = new Runnable() {
            @Override
            public void run() {
                timer = Timer.Break;
                breakTimer = new CountDownTimer(breakTime,countDownInterval) {
                    @Override
                    public void onTick(long l) {
                        milliSecondsLeft = l;
                        updateTimer(milliSecondsLeft);
                    }

                    @Override
                    public void onFinish() {
                        timerHandler.post(workRun);
                    }
                }.start();
            }
        };

        longBreakRun = new Runnable(){
            @Override
            public void run() {
                timer = Timer.LongBreak;
                longBreakTimer = new CountDownTimer(longBreakTime,countDownInterval) {
                    @Override
                    public void onTick(long l) {
                        milliSecondsLeft = l;
                        updateTimer(milliSecondsLeft);
                    }

                    @Override
                    public void onFinish() {
                        timerHandler.post(workRun);
                    }
                }.start();
            }
        };
    }
}