package com.userstar.phonekeybasicfunctiondemokotlin.services.userstar;

public class triv {
    public triv() {
    }

    public static String get_triv(String USID, String Key, String ls_counter, String T1) {
        try {
            String ls_AC3 = "";
            String ls_usid_2 = "";
            String ls_key_2 = "";
            String ls_t1_2 = "";
            String ls_counter_2 = "";

            int i;
            for(i = 0; i < 20; ++i) {
                ls_usid_2 = ls_usid_2 + d_16_2String(USID.substring(i, i + 1));
            }

            for(i = 0; i < 12; ++i) {
                ls_key_2 = ls_key_2 + d_16_2String(Key.substring(i, i + 1));
            }

            for(i = 0; i < 8; ++i) {
                ls_counter_2 = ls_counter_2 + d_16_2String(ls_counter.substring(i, i + 1));
            }

            for(i = 0; i < 20; ++i) {
                ls_t1_2 = ls_t1_2 + d_16_2String(T1.substring(i, i + 1));
            }

            String s1 = "";
            String s2 = "";
            String s3 = "";
            String s_all = "";
            String z_all = "";
            String AC3_2 = "";
            s1 = ls_usid_2 + "0000000000000";
            s2 = ls_counter_2 + ls_key_2 + "0000";

            for(i = 1; i <= 108; ++i) {
                s3 = s3 + "0";
            }

            s3 = s3 + "111";
            s_all = s1 + s2 + s3;

            String t11;
            String t12;
            String t13;
            for(i = 1; i <= 1152; ++i) {
                t11 = xor(xor(xor(s_all.substring(65, 66), and(s_all.substring(90, 91), s_all.substring(91, 92))), s_all.substring(92, 93)), s_all.substring(170, 171));
                t12 = xor(xor(xor(s_all.substring(161, 162), and(s_all.substring(174, 175), s_all.substring(175, 176))), s_all.substring(176, 177)), s_all.substring(263, 264));
                t13 = xor(xor(xor(s_all.substring(242, 243), and(s_all.substring(285, 286), s_all.substring(286, 287))), s_all.substring(287, 288)), s_all.substring(68, 69));
                s1 = t13 + s1.substring(0, 92);
                s2 = t11 + s2.substring(0, 83);
                s3 = t12 + s3.substring(0, 110);
                s_all = s1 + s2 + s3;
            }

            for(i = 0; i < 80; ++i) {
                t11 = xor(s_all.substring(65, 66), s_all.substring(92, 93));
                t12 = xor(s_all.substring(161, 162), s_all.substring(176, 177));
                t13 = xor(s_all.substring(242, 243), s_all.substring(287, 288));
                String z = xor(xor(t11, t12), t13);
                z_all = z_all + z;
                t11 = xor(xor(t11, and(s_all.substring(90, 91), s_all.substring(91, 92))), s_all.substring(170, 171));
                t12 = xor(xor(t12, and(s_all.substring(174, 175), s_all.substring(175, 176))), s_all.substring(263, 264));
                t13 = xor(xor(t13, and(s_all.substring(285, 286), s_all.substring(286, 287))), s_all.substring(68, 69));
                s1 = t13 + s1.substring(0, 92);
                s2 = t11 + s2.substring(0, 83);
                s3 = t12 + s3.substring(0, 110);
                s_all = s1 + s2 + s3;
                AC3_2 = AC3_2 + xor(z, ls_t1_2.substring(i, i + 1));
            }

            for(i = 0; i < 20; ++i) {
                ls_AC3 = ls_AC3 + d_2_16String(AC3_2.substring(i * 4, i * 4 + 4));
            }

            return ls_AC3;
        } catch (Exception var20) {
            return var20.toString();
        }
    }

    public static String get_triv(String USID, String Key, String ls_counter) {
        try {
            String ls_AC3 = "";
            String ls_usid_2 = "";
            String ls_key_2 = "";
            String ls_counter_2 = "";

            int i;
            for(i = 0; i < 20; ++i) {
                ls_usid_2 = ls_usid_2 + d_16_2String(USID.substring(i, i + 1));
            }

            for(i = 0; i < 12; ++i) {
                ls_key_2 = ls_key_2 + d_16_2String(Key.substring(i, i + 1));
            }

            for(i = 0; i < 8; ++i) {
                ls_counter_2 = ls_counter_2 + d_16_2String(ls_counter.substring(i, i + 1));
            }

            String s1 = "";
            String s2 = "";
            String s3 = "";
            String s_all = "";
            String z_all = "";
            String AC3_2 = "";
            s1 = ls_usid_2 + "0000000000000";
            s2 = ls_counter_2 + ls_key_2 + "0000";

            for(i = 1; i <= 108; ++i) {
                s3 = s3 + "0";
            }

            s3 = s3 + "111";
            s_all = s1 + s2 + s3;

            String t11;
            String t12;
            String t13;
            for(i = 1; i <= 1152; ++i) {
                t11 = xor(xor(xor(s_all.substring(65, 66), and(s_all.substring(90, 91), s_all.substring(91, 92))), s_all.substring(92, 93)), s_all.substring(170, 171));
                t12 = xor(xor(xor(s_all.substring(161, 162), and(s_all.substring(174, 175), s_all.substring(175, 176))), s_all.substring(176, 177)), s_all.substring(263, 264));
                t13 = xor(xor(xor(s_all.substring(242, 243), and(s_all.substring(285, 286), s_all.substring(286, 287))), s_all.substring(287, 288)), s_all.substring(68, 69));
                s1 = t13 + s1.substring(0, 92);
                s2 = t11 + s2.substring(0, 83);
                s3 = t12 + s3.substring(0, 110);
                s_all = s1 + s2 + s3;
            }

            for(i = 0; i < 80; ++i) {
                t11 = xor(s_all.substring(65, 66), s_all.substring(92, 93));
                t12 = xor(s_all.substring(161, 162), s_all.substring(176, 177));
                t13 = xor(s_all.substring(242, 243), s_all.substring(287, 288));
                String z = xor(xor(t11, t12), t13);
                z_all = z_all + z;
                t11 = xor(xor(t11, and(s_all.substring(90, 91), s_all.substring(91, 92))), s_all.substring(170, 171));
                t12 = xor(xor(t12, and(s_all.substring(174, 175), s_all.substring(175, 176))), s_all.substring(263, 264));
                t13 = xor(xor(t13, and(s_all.substring(285, 286), s_all.substring(286, 287))), s_all.substring(68, 69));
                s1 = t13 + s1.substring(0, 92);
                s2 = t11 + s2.substring(0, 83);
                s3 = t12 + s3.substring(0, 110);
                s_all = s1 + s2 + s3;
            }

            for(i = 0; i < 20; ++i) {
                ls_AC3 = ls_AC3 + d_2_16String(AC3_2.substring(i * 4, i * 4 + 4));
            }

            return ls_AC3;
        } catch (Exception var18) {
            return var18.toString();
        }
    }

    public static String xor(String a, String b) {
        String c = "";
        if (a.equals("0") && b.equals("0")) {
            c = "0";
        }

        if (a.equals("1") && b.equals("0")) {
            c = "1";
        }

        if (a.equals("0") && b.equals("1")) {
            c = "1";
        }

        if (a.equals("1") && b.equals("1")) {
            c = "0";
        }

        return c;
    }

    public static String and(String a, String b) {
        String c = "";
        if (a.equals("0") && b.equals("0")) {
            c = "0";
        }

        if (a.equals("1") && b.equals("0")) {
            c = "0";
        }

        if (a.equals("0") && b.equals("1")) {
            c = "0";
        }

        if (a.equals("1") && b.equals("1")) {
            c = "1";
        }

        return c;
    }

    public static String d_2_16String(String a) {
        String ls_16 = "";
        if (a.equals("0000")) {
            ls_16 = "0";
        } else if (a.equals("0001")) {
            ls_16 = "1";
        } else if (a.equals("0010")) {
            ls_16 = "2";
        } else if (a.equals("0011")) {
            ls_16 = "3";
        } else if (a.equals("0100")) {
            ls_16 = "4";
        } else if (a.equals("0101")) {
            ls_16 = "5";
        } else if (a.equals("0110")) {
            ls_16 = "6";
        } else if (a.equals("0111")) {
            ls_16 = "7";
        } else if (a.equals("1000")) {
            ls_16 = "8";
        } else if (a.equals("1001")) {
            ls_16 = "9";
        } else if (a.equals("1010")) {
            ls_16 = "A";
        } else if (a.equals("1011")) {
            ls_16 = "B";
        } else if (a.equals("1100")) {
            ls_16 = "C";
        } else if (a.equals("1101")) {
            ls_16 = "D";
        } else if (a.equals("1110")) {
            ls_16 = "E";
        } else if (a.equals("1111")) {
            ls_16 = "F";
        } else {
            ls_16 = "ERROR";
        }

        return ls_16;
    }

    static String d_16_2String(String a) {
        String ls_2 = "";
        if (a.equals("0")) {
            ls_2 = "0000";
        } else if (a.equals("1")) {
            ls_2 = "0001";
        } else if (a.equals("2")) {
            ls_2 = "0010";
        } else if (a.equals("3")) {
            ls_2 = "0011";
        } else if (a.equals("4")) {
            ls_2 = "0100";
        } else if (a.equals("5")) {
            ls_2 = "0101";
        } else if (a.equals("6")) {
            ls_2 = "0110";
        } else if (a.equals("7")) {
            ls_2 = "0111";
        } else if (a.equals("8")) {
            ls_2 = "1000";
        } else if (a.equals("9")) {
            ls_2 = "1001";
        } else if (a.equals("A")) {
            ls_2 = "1010";
        } else if (a.equals("B")) {
            ls_2 = "1011";
        } else if (a.equals("C")) {
            ls_2 = "1100";
        } else if (a.equals("D")) {
            ls_2 = "1101";
        } else if (a.equals("E")) {
            ls_2 = "1110";
        } else if (a.equals("F")) {
            ls_2 = "1111";
        } else {
            ls_2 = "ERROR";
        }

        return ls_2;
    }
}

