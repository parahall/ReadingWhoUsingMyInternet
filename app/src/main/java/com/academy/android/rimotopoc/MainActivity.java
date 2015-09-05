package com.academy.android.rimotopoc;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import static junit.framework.Assert.assertTrue;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String PROC_NET_TCP = "/proc/net/tcp";

    private TextView mProcStatTextView;

    private boolean isNotRunning = true;

    private Thread mThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProcStatTextView = (TextView) findViewById(R.id.tv_am_proc_stat);
        findViewById(R.id.btn_am_load).setOnClickListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mThread != null) {
            mThread.interrupt();
        }
        isNotRunning = true;
        mThread = null;
    }

    @Override
    public void onClick(View v) {
        startMonitoringProc();
    }

    private void startMonitoringProc() {
        if (isNotRunning) {
            isNotRunning = false;
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        List<ParsedProcEntry> parse = null;
                        try {
                            parse = ParsedProcEntry.parse(PROC_NET_TCP);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (parse != null) {
                            StringBuilder builder = new StringBuilder();
                            String time = currentTime();
                            builder.append("Last refresh at: ").append(time)
                                    .append(System.getProperty("line.separator"))
                                    .append(System.getProperty("line.separator"));
                            for (int i = 0; i < parse.size(); i++) {
                                int uid = parse.get(i).uid;
                                builder.append(uid).
                                        append(" - ").
                                        append(getPackageManager().getNameForUid(uid)).
                                        append(System.getProperty("line.separator"));
                            }
                            postResults(builder.toString());
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                }
            });
            mThread.start();
        }
    }

    @NonNull
    private String currentTime() {
        Date date = new Date(System.currentTimeMillis());
        DateFormat formatter = SimpleDateFormat.getTimeInstance();
        return formatter.format(date);
    }

    private void postResults(final String result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProcStatTextView.setText(result);
            }
        });
    }

    private static class ParsedProcEntry {

        private final InetAddress localAddress;

        private final int port;

        private final String state;

        private final int uid;

        private ParsedProcEntry(InetAddress addr, int port, String state, int uid) {
            this.localAddress = addr;
            this.port = port;
            this.state = state;
            this.uid = uid;
        }

        private static List<ParsedProcEntry> parse(String procFilePath) throws IOException {
            List<ParsedProcEntry> retval = new ArrayList<>();
            /*
            * Sample output of "cat /proc/net/tcp" on emulator:
            *
            * sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  ...
            * 0: 0100007F:13AD 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0   ...
            * 1: 00000000:15B3 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0   ...
            * 2: 0F02000A:15B3 0202000A:CE8A 01 00000000:00000000 00:00000000 00000000     0   ...
            *
            */
            File procFile = new File(procFilePath);
            Scanner scanner = null;
            try {
                scanner = new Scanner(procFile);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    // Skip column headers
                    if (line.startsWith("sl")) {
                        continue;
                    }
                    String[] fields = line.split("\\s+");
                    final int expectedNumColumns = 12;
                    assertTrue(procFilePath + " should have at least " + expectedNumColumns
                            + " columns of output " + Arrays.toString(fields), fields.length >= expectedNumColumns);
                    String state = fields[3];
                    int uid = Integer.parseInt(fields[7]);
                    InetAddress localIp = addrToInet(fields[1].split(":")[0]);
                    int localPort = Integer.parseInt(fields[1].split(":")[1], 16);
                    retval.add(new ParsedProcEntry(localIp, localPort, state, uid));
                }
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
            return retval;
        }

        /**
         * Convert a string stored in little endian format to an IP address.
         */
        private static InetAddress addrToInet(String s) throws UnknownHostException {
            int len = s.length();
            if (len != 8 && len != 32) {
                throw new IllegalArgumentException(len + "");
            }
            byte[] retval = new byte[len / 2];
            for (int i = 0; i < len / 2; i += 4) {
                retval[i] = (byte) ((Character.digit(s.charAt(2 * i + 6), 16) << 4)
                        + Character.digit(s.charAt(2 * i + 7), 16));
                retval[i + 1] = (byte) ((Character.digit(s.charAt(2 * i + 4), 16) << 4)
                        + Character.digit(s.charAt(2 * i + 5), 16));
                retval[i + 2] = (byte) ((Character.digit(s.charAt(2 * i + 2), 16) << 4)
                        + Character.digit(s.charAt(2 * i + 3), 16));
                retval[i + 3] = (byte) ((Character.digit(s.charAt(2 * i), 16) << 4)
                        + Character.digit(s.charAt(2 * i + 1), 16));
            }
            return InetAddress.getByAddress(retval);
        }
    }

}
