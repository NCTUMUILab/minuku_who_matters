package edu.nctu.minuku.stream;

import java.util.ArrayList;
import java.util.List;

import edu.nctu.minuku.model.DataRecord.BatteryDataRecord;
import edu.nctu.minukucore.model.DataRecord;
import edu.nctu.minukucore.stream.AbstractStreamFromDevice;

/**
 * Created by Lawrence on 2017/8/22.
 */

public class BatteryStream extends AbstractStreamFromDevice<BatteryDataRecord> {
    public BatteryStream(int maxSize) {
        super(maxSize);
    }

    @Override
    public List<Class<? extends DataRecord>> dependsOnDataRecordType() {
        return new ArrayList<>();
    }
}
