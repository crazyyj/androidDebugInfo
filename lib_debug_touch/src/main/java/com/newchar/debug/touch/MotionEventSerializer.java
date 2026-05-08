package com.newchar.debug.touch;

import android.os.Parcel;
import android.view.MotionEvent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MotionEventSerializer {

    private static final String MAGIC = "MTES";
    private static final short VERSION = 0x0001;
    private static final int HEADER_SIZE = 128;
    private static final int MAX_ACTIVITY_NAME_LENGTH = 64;

    public static class TouchRecordHeader {
        public long recordStartTime;
        public String activityName;
        public int eventCount;
    }

    public static class TouchEventRecord {
        public MotionEvent motionEvent;
        public long timestamp;
        public int action;
    }

    public static class TouchRecordWriter {
        private final File mFile;
        private final DataOutputStream mDataOutput;
        private final BufferedOutputStream mBufferedOutput;
        private final TouchRecordHeader mHeader;
        private int mWrittenEventCount;
        private boolean mClosed;

        public TouchRecordWriter(File file, String activityName) throws IOException {
            mFile = file;
            mHeader = new TouchRecordHeader();
            mHeader.recordStartTime = System.currentTimeMillis();
            mHeader.activityName = activityName != null ? activityName : "";
            mHeader.eventCount = 0;
            mWrittenEventCount = 0;
            mClosed = false;

            FileOutputStream fos = new FileOutputStream(file, false);
            mBufferedOutput = new BufferedOutputStream(fos, 8192);
            mDataOutput = new DataOutputStream(mBufferedOutput);

            writeHeader();
        }

        private void writeHeader() throws IOException {
            byte[] headerBytes = new byte[HEADER_SIZE];
            
            // Magic number
            System.arraycopy(MAGIC.getBytes(), 0, headerBytes, 0, Math.min(4, MAGIC.length()));
            
            // Version
            headerBytes[4] = (byte) (VERSION >> 8);
            headerBytes[5] = (byte) VERSION;
            
            // Record start time
            long startTime = mHeader.recordStartTime;
            for (int i = 0; i < 8; i++) {
                headerBytes[6 + i] = (byte) (startTime >> (56 - i * 8));
            }
            
            // Activity name length and name
            byte[] activityBytes = mHeader.activityName.getBytes("UTF-8");
            int nameLength = Math.min(activityBytes.length, MAX_ACTIVITY_NAME_LENGTH);
            headerBytes[14] = (byte) (nameLength >> 24);
            headerBytes[15] = (byte) (nameLength >> 16);
            headerBytes[16] = (byte) (nameLength >> 8);
            headerBytes[17] = (byte) nameLength;
            
            System.arraycopy(activityBytes, 0, headerBytes, 18, nameLength);
            
            // Write header
            mDataOutput.write(headerBytes);
            mDataOutput.flush();
        }

        public synchronized void writeEvent(MotionEvent event) throws IOException {
            if (mClosed || event == null) {
                return;
            }

            // Serialize MotionEvent using Parcel
            Parcel parcel = Parcel.obtain();
            try {
                event.writeToParcel(parcel, 0);
                byte[] eventData = parcel.marshall();

                // Write event block
                // Event data size (4 bytes)
                mDataOutput.writeInt(eventData.length);
                
                // Event data
                mDataOutput.write(eventData);
                
                // Timestamp (8 bytes)
                mDataOutput.writeLong(System.currentTimeMillis());
                
                // Action type (1 byte)
                mDataOutput.writeByte((byte) event.getAction());
                
                mDataOutput.flush();
                mWrittenEventCount++;
            } finally {
                parcel.recycle();
            }
        }

        public void close() throws IOException {
            if (mClosed) {
                return;
            }
            mClosed = true;
            
            try {
                // Update event count in header
                RandomAccessFile raf = new RandomAccessFile(mFile, "rw");
                try {
                    raf.seek(18 + MAX_ACTIVITY_NAME_LENGTH);
                    raf.writeInt(mWrittenEventCount);
                } finally {
                    raf.close();
                }
            } catch (IOException e) {
                // Ignore header update error
            }
            
            mDataOutput.close();
            mBufferedOutput.close();
        }

        public int getWrittenEventCount() {
            return mWrittenEventCount;
        }

        public File getFile() {
            return mFile;
        }
    }

    public static class TouchRecordReader {
        private final File mFile;
        private final DataInputStream mDataInput;
        private final BufferedInputStream mBufferedInput;
        private final TouchRecordHeader mHeader;
        private int mReadEventCount;
        private boolean mClosed;

        public TouchRecordReader(File file) throws IOException {
            mFile = file;
            FileInputStream fis = new FileInputStream(file);
            mBufferedInput = new BufferedInputStream(fis, 8192);
            mDataInput = new DataInputStream(mBufferedInput);
            mReadEventCount = 0;
            mClosed = false;
            
            mHeader = readHeader();
        }

        private TouchRecordHeader readHeader() throws IOException {
            TouchRecordHeader header = new TouchRecordHeader();
            
            // Read and verify magic
            byte[] magicBytes = new byte[4];
            mDataInput.readFully(magicBytes);
            String magic = new String(magicBytes);
            if (!MAGIC.equals(magic)) {
                throw new IOException("Invalid file format: bad magic number");
            }
            
            // Read version
            short version = mDataInput.readShort();
            if (version != VERSION) {
                throw new IOException("Unsupported version: " + version);
            }
            
            // Read start time
            header.recordStartTime = mDataInput.readLong();
            
            // Read activity name
            int nameLength = mDataInput.readInt();
            if (nameLength > 0 && nameLength <= MAX_ACTIVITY_NAME_LENGTH) {
                byte[] nameBytes = new byte[nameLength];
                mDataInput.readFully(nameBytes);
                header.activityName = new String(nameBytes, "UTF-8");
            } else {
                header.activityName = "";
            }
            
            // Skip padding to reach event count position
            long skipBytes = 18 + MAX_ACTIVITY_NAME_LENGTH - (6 + 8 + 4 + nameLength);
            if (skipBytes > 0) {
                mDataInput.skipBytes((int) skipBytes);
            }
            
            // Read event count
            header.eventCount = mDataInput.readInt();
            
            return header;
        }

        public synchronized TouchEventRecord readEvent() throws IOException {
            if (mClosed) {
                return null;
            }
            
            try {
                // Check if we've read all events
                if (mHeader.eventCount > 0 && mReadEventCount >= mHeader.eventCount) {
                    return null;
                }
                
                // Read event data size
                int eventDataSize = mDataInput.readInt();
                if (eventDataSize <= 0 || eventDataSize > 10240) { // Reasonable size limit
                    return null;
                }
                
                // Read event data
                byte[] eventData = new byte[eventDataSize];
                mDataInput.readFully(eventData);
                
                // Deserialize MotionEvent from Parcel
                Parcel parcel = Parcel.obtain();
                try {
                    parcel.unmarshall(eventData, 0, eventData.length);
                    parcel.setDataPosition(0);
                    MotionEvent motionEvent = MotionEvent.CREATOR.createFromParcel(parcel);
                    
                    // Read metadata
                    long timestamp = mDataInput.readLong();
                    int action = mDataInput.readByte() & 0xFF;
                    
                    TouchEventRecord record = new TouchEventRecord();
                    record.motionEvent = motionEvent;
                    record.timestamp = timestamp;
                    record.action = action;
                    
                    mReadEventCount++;
                    return record;
                } finally {
                    parcel.recycle();
                }
            } catch (EOFException e) {
                return null;
            }
        }

        public List<TouchEventRecord> readAllEvents() throws IOException {
            List<TouchEventRecord> events = new ArrayList<>();
            TouchEventRecord event;
            while ((event = readEvent()) != null) {
                events.add(event);
            }
            return events;
        }

        public void close() throws IOException {
            if (mClosed) {
                return;
            }
            mClosed = true;
            mDataInput.close();
            mBufferedInput.close();
        }

        public TouchRecordHeader getHeader() {
            return mHeader;
        }

        public int getReadEventCount() {
            return mReadEventCount;
        }

        public File getFile() {
            return mFile;
        }
    }

    public static boolean isValidTouchRecordFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        
        DataInputStream input = null;
        try {
            input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            byte[] magicBytes = new byte[4];
            input.readFully(magicBytes);
            String magic = new String(magicBytes);
            return MAGIC.equals(magic);
        } catch (IOException e) {
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static File createTouchRecordFile(File parentDir, String activityName) {
        if (parentDir == null || activityName == null || activityName.isEmpty()) {
            return null;
        }
        
        String fileName = String.format("touch_%s_%d.mtes", 
                activityName, System.currentTimeMillis());
        return new File(parentDir, fileName);
    }
}