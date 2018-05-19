package edu.nctu.minuku.streamgenerator;

import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import com.amplitude.api.Amplitude;
import com.amplitude.api.Identify;

import org.greenrobot.eventbus.EventBus;

import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.dao.AccessibilityDataRecordDAO;
import edu.nctu.minuku.logger.Log;
import edu.nctu.minuku.manager.MinukuDAOManager;
import edu.nctu.minuku.model.DataRecord.AccessibilityDataRecord;
import edu.nctu.minuku.service.MobileAccessibilityService;
import edu.nctu.minuku.stream.BatteryStream;
import edu.nctu.minukucore.dao.DAOException;
import edu.nctu.minukucore.exception.StreamAlreadyExistsException;
import edu.nctu.minukucore.exception.StreamNotFoundException;
import edu.nctu.minukucore.stream.Stream;

import static edu.nctu.minuku.manager.MinukuStreamManager.getInstance;

/**
 * Created by Lawrence on 2017/9/6.
 */

public class AccessibilityStreamGenerator extends AndroidStreamGenerator<AccessibilityDataRecord> {

    private final String TAG = "AccessibilityStreamGenerator";
    private Stream mStream;
    private Context mContext;
    AccessibilityDataRecordDAO mDAO;
    MobileAccessibilityService mobileAccessibilityService;

    private String pack;
    private String text;
    private String type;
    private String extra;
    private String deviceId;

    public AccessibilityStreamGenerator(Context applicationContext){
        super(applicationContext);
        this.mContext = applicationContext;
        this.mStream = new BatteryStream(Constants.DEFAULT_QUEUE_SIZE);
        this.mDAO = MinukuDAOManager.getInstance().getDaoFor(AccessibilityDataRecord.class);


        TelephonyManager telephonyManager;
        telephonyManager = (TelephonyManager) mContext.getSystemService(Context.
                TELEPHONY_SERVICE);

        try {
            deviceId = telephonyManager.getDeviceId();
        } catch (SecurityException e){
            deviceId = "null";
        }
        Amplitude.getInstance().initialize(this.mContext, "357d2125a984bc280669e6229646816c");
        Identify identify = new Identify().set("DEVICE_ID", deviceId);
        Amplitude.getInstance().identify(identify);
        Amplitude.getInstance().logEvent("INIT_AccessibilityStreamGenerator");

        mobileAccessibilityService = new MobileAccessibilityService(this);

        pack = text = type = extra = "";

        this.register();
    }

    @Override
    public void register() {
        Log.d(TAG, "Registring with StreamManage");

        try {
            getInstance().register(mStream, AccessibilityDataRecord.class, this);
        } catch (StreamNotFoundException streamNotFoundException) {
            Log.e(TAG, "One of the streams on which" +
                    "AccessibilityDataRecord/AccessibilityStream depends in not found.");
        } catch (StreamAlreadyExistsException streamAlreadyExistsException) {
            Log.e(TAG, "Another stream which provides" +
                    " AccessibilityDataRecord/AccessibilityStream is already registered.");
        }
    }

    private void activateAccessibilityService() {

        Log.d(TAG, "testing logging task and requested activateAccessibilityService");
        Intent intent = new Intent(mContext, MobileAccessibilityService.class);
        mContext.startService(intent);

    }


    @Override
    public Stream<AccessibilityDataRecord> generateNewStream() {
        return mStream;
    }

    @Override
    public boolean updateStream() {
        Log.d(TAG, "updateStream called");

        AccessibilityDataRecord accessibilityDataRecord
                = new AccessibilityDataRecord(pack, text, type, extra);
        mStream.add(accessibilityDataRecord);
        Log.d(TAG,"pack = "+pack+" text = "+text+" type = "+type+" extra = "+extra);
        Log.d(TAG, "Accessibility to be sent to event bus" + accessibilityDataRecord);
        // also post an event.
        EventBus.getDefault().post(accessibilityDataRecord);
        try {
            if(!type.isEmpty()){
                mDAO.add(accessibilityDataRecord);
                Log.d(TAG, "updateStream add success");
                Amplitude.getInstance().logEvent("Accessibility_updateStream_SUCCESS");
            }

        } catch (DAOException e) {
            e.printStackTrace();
            return false;
        }catch (NullPointerException e){ //Sometimes no data is normal
            e.printStackTrace();
            return false;
        }

        pack = text = type = extra = "";

        return false;
    }

    @Override
    public long getUpdateFrequency() {
        return 1;
    }

    @Override
    public void sendStateChangeEvent() {

    }

    public void setLatestInAppAction(String pack, String text, String type, String extra){

        this.pack = pack;
        this.text = text;
        this.type = type;
        this.extra = extra;

    }

    @Override
    public void onStreamRegistration() {

        activateAccessibilityService();

    }

    @Override
    public void offer(AccessibilityDataRecord dataRecord) {

    }
}
