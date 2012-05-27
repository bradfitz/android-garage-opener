package com.danga.garagedoor.anpher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;


public class AnpherActivity extends Activity {

    TextView tv;
    Handler uiHandler;
	
    BlockingQueue<Parcel> toChild = new LinkedBlockingQueue<Parcel>();
    AtomicReference<Process> goProcess = new AtomicReference<Process>();
    AtomicReference<OutputStream> toChildRef = new AtomicReference<OutputStream>();
    private String TAG = "AnpherActivity";
	
    private String binaryPath(String suffix) {
        return getBaseContext().getFilesDir().getAbsolutePath() + "/" + suffix;
    }

    private void copyBinary() {
        String src = "anpher";
        maybeCopyFile(src, "anphergo");
    }
	
    private void maybeCopyFile(String src, String dstSuffix) {
        String fullPath = binaryPath(dstSuffix);
        if (new File(fullPath).exists()) {
            Log.d(TAG, "file " + fullPath + " already exists");
        }
        try {
            InputStream is = getAssets().open(src);
            FileOutputStream fos = getBaseContext().openFileOutput(dstSuffix + ".writing", MODE_PRIVATE);

            byte[] buf = new byte[8192];
            int offset;
            while ((offset = is.read(buf))>0) {
                fos.write(buf, 0, offset);
            }
            is.close(); 
            fos.flush();
            fos.close();
            String writingFile = fullPath + ".writing";
            Log.d(TAG, "wrote out " + writingFile);
            Runtime.getRuntime().exec("chmod 0777 " + writingFile);
            Log.d(TAG, "did chmod 0700 on " + writingFile);
            Runtime.getRuntime().exec("mv " + writingFile + " " + fullPath);
            Log.d(TAG, "Moved writing file to " + fullPath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
	
    @Override
	public void onPause() {
        super.onPause();
        sendEvent(newLifeCycleEvent(this, "pause"));
        Process p = goProcess.get();
        if (p != null) {
            p.destroy();
            goProcess.set(null);
        }
    }
	
    @Override
	public void onResume() {
        super.onResume();
        sendEvent(newLifeCycleEvent(this, "resume"));
        condStartHelper();
    }
	
    private void condStartHelper() {
        final Process p = goProcess.get();
        if (p != null) {
            return;
        }
        Thread child = new Thread() {
        	@Override
                    public void run() {
                    Process process = null;
                    Thread writeThread = null;
                    try {
                        process = new ProcessBuilder()
                            .command(binaryPath("anphergo"))
                            .redirectErrorStream(false)
                            .start();
                        goProcess.set(process);
                        InputStream in = process.getInputStream();
                        new CopyToAndroidLogThread(process.getErrorStream()).start();
                        writeThread = new WriteToChildThread(process.getOutputStream());
                        writeThread.start();
	
                        BufferedReader br = new BufferedReader(new InputStreamReader(in));
                        while (true) {
                            final String line = br.readLine();
                            if (line == null) {
                                Log.d(TAG, "null line from child process.");
                                return;
                            }
                            if (line.startsWith("LOG: ")) {
                                Log.d(TAG+"/Child", line.substring(5));
                                continue;
                            }
                            uiHandler.post(new Runnable() {
                                    public void run() {
                                        tv.setText(line);
                                    }
                                });
                        }
	
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } finally {
                        if (process != null) {
                            process.destroy();
                        }
                        goProcess.compareAndSet(p, null);
                        if (writeThread != null) {
                            writeThread.interrupt();
                        }
                    }        
        	}
            };
        child.start();
    }
	
    private class WriteToChildThread extends Thread {
        private final OutputStream mOut;
		
        public WriteToChildThread(OutputStream dst) {
            mOut = dst;
        }
		
        @Override public void run() {
            while (true) {
                Parcel p;
                try {
                    p = toChild.poll(10, TimeUnit.SECONDS);
                } catch (InterruptedException e1) {
                    Log.d(TAG, "InterruptedException during polling toChild queue.");
                    return;
                }
                if (p == null) {
                    continue;
                }
                byte[] buf = p.marshall();
                Log.d(TAG, "Writing " + buf.length + " bytes to child");
                try {
                    mOut.write(buf);
                } catch (IOException e) {
                    Log.d(TAG, "Write to child failed: " + e.toString());
                    return;
                }
                p.recycle();
            }
        }
    }
	
    private class CopyToAndroidLogThread extends Thread {
        private final BufferedReader mBufIn;
		
        public CopyToAndroidLogThread(InputStream in) {
            mBufIn = new BufferedReader(new InputStreamReader(in));
        }
		
        @Override 
            public void run() {
            String tag = TAG + "/child-stderr";
            while (true) {
                String line = null;
                try {
                    line = mBufIn.readLine();
                } catch (IOException e) {
                    Log.d(tag, "Exception: " + e.toString());
                    return;
                }
                if (line == null) {
                    Log.d(tag, "null line from child stderr.");
                    return;
                }
                Log.d(tag, line);
            }
        }
    }

    private static Parcel newEvent(String name) {
        Parcel p = Parcel.obtain();
        p.writeString(name);
        return p;
    }
	
    private static Parcel newClickEvent(int id) {
        Parcel p = newEvent("click");
        p.writeInt(id);
        return p;
    }
	
    private static Parcel newLifeCycleEvent(Context c, String lifeEvent) {
        Parcel p = newEvent("life");
        p.writeInt((c instanceof Activity) ? 1 : 0);
        p.writeString(c.getPackageName());
        p.writeString(lifeEvent);
        return p;
    }

    private void sendEvent(Parcel evt) {
        toChild.add(evt);
    }
	
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);        
            
        copyBinary();
        uiHandler = new Handler();
       
        condStartHelper();
        sendEvent(newLifeCycleEvent(this, "create"));

        setupButtonHandler(R.id.button1);
        setupButtonHandler(R.id.button2);

        tv = (TextView) findViewById(R.id.tv1);
    }
    
    private void setupButtonHandler(int id) {
        final Button button = (Button) findViewById(id);
        if (button == null) {
            Log.v(TAG, "failed to find button");
            return;
        }
        button.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    tv.setText("java clicked button " + button.getId());
                    sendEvent(newClickEvent(button.getId()));
				
                    Log.d(TAG, "button1 = " + getResources().getIdentifier("button1", "id", getClass().getPackage().getName()));
                }
            });
    } 
}
	