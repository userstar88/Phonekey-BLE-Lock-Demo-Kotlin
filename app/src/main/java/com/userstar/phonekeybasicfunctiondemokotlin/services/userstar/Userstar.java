package com.userstar.phonekeybasicfunctiondemokotlin.services.userstar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Userstar {
    public static String random_value(int difficult, int num) {
        String ls_random = "";

        for(int i = 0; i < num; ++i) {
            if (difficult == 1) {
                ls_random = ls_random + String.valueOf((int)(Math.random() * 10.0D));
            } else {
                String[] readomHard;
                int readomWordIndex;
                if (difficult == 2) {
                    readomHard = new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
                    readomWordIndex = (int)(Math.random() * 16.0D);
                    ls_random = ls_random + readomHard[readomWordIndex];
                } else if (difficult == 3) {
                    char readomLetter = (char)((int)(Math.random() * 26.0D + 97.0D));
                    ls_random = ls_random + String.valueOf(readomLetter);
                } else if (difficult == 4) {
                    readomHard = new String[20];
                    readomWordIndex = (int)(Math.random() * 19.0D);

                    for(int j = 0; j < 20; ++j) {
                        int readomWordNum = (int)(Math.random() * 10.0D);
                        char readomLetter = (char)((int)(Math.random() * 26.0D + 97.0D));
                        if (readomWordNum % 2 == 0) {
                            readomHard[j] = readomWordNum + "";
                        } else {
                            readomHard[j] = String.valueOf(readomLetter);
                        }
                    }

                    ls_random = ls_random + readomHard[readomWordIndex];
                }
            }
        }

        return ls_random.toUpperCase();
    }

    public static String[] encryptMasterPassword(String deviceName, String T1, String masterPassword) {
        String ls_A2 = "00000000000000000000000000000000";
        String ls_counter = random_value(2, 2).toUpperCase();
        String ls_mac = AES.parseAscii2HexStr(deviceName.substring(3, 11)).toUpperCase();
        String ls_t2 = triv.get_triv(T1, ls_mac.substring(0, 12), ls_mac.substring(12, 16) + ls_counter + ls_counter, "00000000000000000000");
        String ls_entmaster = AES.parseHexStr2Ascii(masterPassword + masterPassword);
        String ls_t3 = AES.parseHexStr2Ascii(ls_t2 + ls_t2.substring(0, 12));

        try {
            ls_A2 = AES.Encrypt2(ls_entmaster, ls_t3);
        } catch (Exception var12) {
            var12.printStackTrace();
        }
        return new String[] {ls_A2, ls_counter};
    }

    public static byte[] toHexByteArrayWithLength(String data) {
        int len;
        if (data.length() % 2 != 0) {
            len = data.length() / 2 + 1;
        } else {
            len = data.length() / 2;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Integer.toHexString(len));
        if (sb.length() < 2) {
            sb.insert(0, '0');
        }

        String dataWithLength = sb.append(data).toString();
        dataWithLength = dataWithLength.replaceAll("\\s+", "");
        if (dataWithLength.length() % 2 != 0) {
            dataWithLength = dataWithLength + "0";
        }

        int hexLen = dataWithLength.length();
        byte[] tmp = new byte[hexLen / 2];

        for(int i = 0; i < tmp.length; ++i) {
            tmp[i] = (byte)Integer.parseInt(dataWithLength.substring(i * 2, i * 2 + 2), 16);
        }

        return tmp;
    }

    public static String get_time_8(String usetime) {
        String nowtime = Long.toHexString(getSec("20160101000000", usetime));
        if (nowtime.length() < 8) {
            nowtime = "00000000".substring(0, 8 - nowtime.length()) + nowtime;
        } else if (nowtime.length() > 8) {
            nowtime = "FFFFFFFF";
        }

        return nowtime;
    }

    public static long getSec(String time1, String time2) {
        long sec = 0L;
        Date date1 = null;
        Date date2 = null;
        if (time1.length() == 14 && time2.length() == 14) {
            SimpleDateFormat myFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

            try {
                date1 = myFormatter.parse(time1);
                date2 = myFormatter.parse(time2);
            } catch (ParseException var9) {
                var9.printStackTrace();
            }

            sec = Math.abs(date1.getTime() - date2.getTime()) / 1000L;
            return sec;
        } else {
            return 0L;
        }
    }

    public static String get_nowtime() {
        SimpleDateFormat sy1 = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date();
        String dateFormat = sy1.format(date);
        return dateFormat;
    }
}
