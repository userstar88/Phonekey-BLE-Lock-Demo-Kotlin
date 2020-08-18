package com.userstar.phonekeybasicfunctiondemokotlin.services.userstar;

import android.nfc.tech.NfcV;
import android.util.Log;

import java.io.IOException;

public class ustag {
    private NfcV nfcv = null;
    private byte[] tagid = null;
    private long time = 10L;
    private int countDown = 20;
    private int wsbRetryLimit = 3;

    public ustag(NfcV paramNfcV) {
        this.nfcv = paramNfcV;
        this.tagid = paramNfcV.getTag().getId();
        if (!this.nfcv.isConnected()) {
            try {
                this.nfcv.connect();
            } catch (IOException var3) {
                var3.printStackTrace();
            }
        }

    }

    public byte[] RSB(int blockno) throws IOException {
        byte[] localObject1 = new byte[]{96, 32, 0, 0, 0, 0, 0, 0, 0, 0, (byte)(blockno & 255)};
        int localCountDown = this.countDown;
        if (this.tagid.length >= 8) {
            System.arraycopy(this.tagid, 0, localObject1, 2, 8);
        }

        if (this.nfcv != null) {

        }

        while(true) {
            try {
                byte[] localObject2 = this.nfcv.transceive(localObject1);
                if (localObject2 != null || !getHexString(localObject2).equals("000017000000")) {
                    return localObject2;
                }

                Log.i("ustag read block 1:", "localObject2==null");
            } catch (IOException var8) {
                Log.i("ustag Exception", "RSB(" + Integer.toString(blockno) + ")" + var8.toString());
                if (localCountDown <= 0) {
                    throw new IOException(var8.toString());
                }

                Log.i("ustag read again", "read agin");
                --localCountDown;

                try {
                    Thread.sleep(this.time);
                } catch (InterruptedException var7) {
                    var7.printStackTrace();
                }
            }
        }
    }

    public byte[] RMBbyRSB(int FBN, int NOB) throws IOException {
        byte[] tmp = new byte[(NOB + 1) * 4];

        for(int i = 0; i < NOB + 1; ++i) {
            System.arraycopy(this.RSB(FBN + i), 2, tmp, i * 4, 4);
        }

        return tmp;
    }

    public byte[] WSB(int blockno, byte[] data) throws IOException, ustag.WSB25FailException {
        byte[] localObject1 = new byte[15];
        int localCountDown = this.countDown;
        localObject1[0] = 32;
        localObject1[1] = 33;
        localObject1[10] = (byte)(blockno & 255);
        if (this.tagid.length >= 8) {
            System.arraycopy(this.tagid, 0, localObject1, 2, 8);
        }

        System.arraycopy(data, 0, localObject1, 11, data.length);
        byte[] arrayOfByte = null;

        while(true) {
            try {
                arrayOfByte = this.nfcv.transceive(localObject1);
                if (arrayOfByte != null) {
                    return arrayOfByte;
                }

                Log.i("ustag read block:", "arrayOfByte==null");
            } catch (Exception var9) {
                Log.i("ustag Exception", "WSB(" + Integer.toString(blockno) + ")" + var9.toString());
                if (blockno == 25) {
                    Log.i("ustag write exception", "WSB 25 fail, retry the whole process");
                    throw new ustag.WSB25FailException(var9.toString());
                }

                if (blockno == 23) {
                    return arrayOfByte;
                }

                if (localCountDown <= 0) {
                    throw new IOException(var9.toString());
                }

                Log.i("ustag write exception", "write agin");
                --localCountDown;

                try {
                    Thread.sleep(this.time);
                } catch (InterruptedException var8) {
                    var8.printStackTrace();
                }
            }
        }
    }

    public void WMBbyWSB(int blockno, byte[] data) throws IOException, ustag.WSB25FailException {
        int NOB = data.length % 4 == 0 ? data.length / 4 : data.length / 4 + 1;
        byte[] newdata = new byte[NOB * 4];
        byte[] tmpdata = new byte[4];
        System.arraycopy(data, 0, newdata, 0, data.length);

        for(int i = NOB - 1; i >= 0; --i) {
            System.arraycopy(newdata, i * 4, tmpdata, 0, 4);
            this.WSB(blockno + i, tmpdata);
        }

    }

    public String cmd(String code) throws Exception {
        String data = "";
        this.WSB(25, hexstr2byte(code + " 00 00 00"));
        data = getHexString(this.RSB(25));
        if ((code.equals("11") || code.equals("17") || code.equals("18")) && data.equals("")) {
            data = "000000000000";
        }

        Log.i("ustag", "cmd(" + code + "):" + data);
        return data.substring(data.length() - 2);
    }

    public String getCounter() throws Exception {
        String data = getHexString(this.RSB(26));
        return RearrangeData(data.substring(data.length() - 8));
    }

    public String getTagType() throws Exception {
        String data = getHexString(this.RSB(27));
        return data.substring(6, 8);
    }

    public String getPrivateState() throws Exception {
        String data = getHexString(this.RSB(27));
        return data.substring(4, 6);
    }

    public String getPSTT() throws Exception {
        String data = getHexString(this.RSB(27));
        return data.substring(4, 8);
    }

    public String getUID() throws Exception {
        return RearrangeData(getHexString(this.tagid));
    }

    public String[] getVdata(String T1) {
        String[] ls_return = new String[3];

        try {
            this.WMBbyWSB(23, hexstr2byte(RearrangeData(T1 + "0002")));
            ls_return[0] = this.getUID();
            String ls_data = getHexString(this.RMBbyRSB(23, 4));
            ls_return[1] = RearrangeData(ls_data.substring(4, 24));
            ls_return[2] = RearrangeData(ls_data.substring(24, 32));
        } catch (Exception var4) {
            var4.printStackTrace();
        }

        return ls_return;
    }

    public void colse() {
        if (this.nfcv.isConnected()) {
            try {
                this.nfcv.close();
            } catch (IOException var2) {
                var2.printStackTrace();
            }
        }

    }

    public String cmdWithRetryFunction(String code, int blockno, byte[] writeData) throws Exception {
        String data = "";
        int i = 0;

        while(i < this.wsbRetryLimit) {
            this.WMBbyWSB(blockno, writeData);

            try {
                this.WSB(25, hexstr2byte(code + " 00 00 00"));
                break;
            } catch (ustag.WSB25FailException var8) {
                Log.i("WSB25 Exception", "Retry:" + (i + 1));
                if (i + 1 == this.wsbRetryLimit) {
                    throw new IOException(var8.toString());
                }

                ++i;
            }
        }

        try {
            Thread.sleep(500L);
        } catch (InterruptedException var7) {
            var7.printStackTrace();
        }

        data = getHexString(this.RSB(25));
        if ((code.equals("11") || code.equals("17") || code.equals("18")) && data.equals("")) {
            data = "000000000000";
        }

        Log.i("ustag", "cmd(" + code + "):" + data);
        return data.substring(data.length() - 2);
    }

    public static String getHexString(byte[] b) {
        String result = "";

        try {
            for(int i = 0; i < b.length; ++i) {
                result = result + Integer.toString((b[i] & 255) + 256, 16).substring(1);
            }
        } catch (Exception var3) {
            var3.printStackTrace();
            result = var3.toString();
        }

        return result;
    }

    public static byte[] hexstr2byte(String hex) {
        hex = hex.replaceAll("\\s+", "");
        if (hex.length() % 2 != 0) {
            hex = hex + "0";
        }

        int len = hex.length();
        byte[] tmp = new byte[len / 2];

        for(int i = 0; i < tmp.length; ++i) {
            tmp[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }

        return tmp;
    }

    public static String RearrangeData(String data) {
        String tmp = "";

        for(int i = data.length() - 2; i >= 0; --i) {
            tmp = tmp + data.substring(i, i + 2);
            --i;
        }

        return tmp;
    }

    class WSB25FailException extends Exception {
        private static final long serialVersionUID = 1429730699996073415L;

        public WSB25FailException(String message) {
            super(message);
        }
    }
}