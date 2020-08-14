package com.userstar.phonekeybasicfunctiondemokotlin.services.userstar

class TrivKotlin {
    companion object {
        fun get_triv(USID: String, Key: String, ls_counter: String, T1: String): String {
            return try {
                var ls_AC3 = ""
                var ls_usid_2 = ""
                var ls_key_2 = ""
                var ls_t1_2 = ""
                var ls_counter_2 = ""
                var i: Int
                i = 0
                while (i < 20) {
                    ls_usid_2 = ls_usid_2 + d_16_2String(USID.substring(i, i + 1))
                    ++i
                }
                i = 0
                while (i < 12) {
                    ls_key_2 = ls_key_2 + d_16_2String(Key.substring(i, i + 1))
                    ++i
                }
                i = 0
                while (i < 8) {
                    ls_counter_2 = ls_counter_2 + d_16_2String(ls_counter.substring(i, i + 1))
                    ++i
                }
                i = 0
                while (i < 20) {
                    ls_t1_2 = ls_t1_2 + d_16_2String(T1.substring(i, i + 1))
                    ++i
                }
                var s1 = ""
                var s2 = ""
                var s3 = ""
                var s_all = ""
                var z_all = ""
                var AC3_2 = ""
                s1 = ls_usid_2 + "0000000000000"
                s2 = ls_counter_2 + ls_key_2 + "0000"
                i = 1
                while (i <= 108) {
                    s3 = s3 + "0"
                    ++i
                }
                s3 = s3 + "111"
                s_all = s1 + s2 + s3
                var t11: String
                var t12: String
                var t13: String
                i = 1
                while (i <= 1152) {
                    t11 = xor(
                        xor(
                            xor(
                                s_all.substring(65, 66),
                                and(s_all.substring(90, 91), s_all.substring(91, 92))
                            ), s_all.substring(92, 93)
                        ), s_all.substring(170, 171)
                    )
                    t12 = xor(
                        xor(
                            xor(
                                s_all.substring(161, 162),
                                and(s_all.substring(174, 175), s_all.substring(175, 176))
                            ), s_all.substring(176, 177)
                        ), s_all.substring(263, 264)
                    )
                    t13 = xor(
                        xor(
                            xor(
                                s_all.substring(242, 243),
                                and(s_all.substring(285, 286), s_all.substring(286, 287))
                            ), s_all.substring(287, 288)
                        ), s_all.substring(68, 69)
                    )
                    s1 = t13 + s1.substring(0, 92)
                    s2 = t11 + s2.substring(0, 83)
                    s3 = t12 + s3.substring(0, 110)
                    s_all = s1 + s2 + s3
                    ++i
                }
                i = 0
                while (i < 80) {
                    t11 = xor(s_all.substring(65, 66), s_all.substring(92, 93))
                    t12 = xor(s_all.substring(161, 162), s_all.substring(176, 177))
                    t13 = xor(s_all.substring(242, 243), s_all.substring(287, 288))
                    val z = xor(xor(t11, t12), t13)
                    z_all = z_all + z
                    t11 = xor(
                        xor(
                            t11,
                            and(s_all.substring(90, 91), s_all.substring(91, 92))
                        ), s_all.substring(170, 171)
                    )
                    t12 = xor(
                        xor(
                            t12,
                            and(s_all.substring(174, 175), s_all.substring(175, 176))
                        ), s_all.substring(263, 264)
                    )
                    t13 = xor(
                        xor(
                            t13,
                            and(s_all.substring(285, 286), s_all.substring(286, 287))
                        ), s_all.substring(68, 69)
                    )
                    s1 = t13 + s1.substring(0, 92)
                    s2 = t11 + s2.substring(0, 83)
                    s3 = t12 + s3.substring(0, 110)
                    s_all = s1 + s2 + s3
                    AC3_2 = AC3_2 + xor(z, ls_t1_2.substring(i, i + 1))
                    ++i
                }
                i = 0
                while (i < 20) {
                    ls_AC3 = ls_AC3 + d_2_16String(AC3_2.substring(i * 4, i * 4 + 4))
                    ++i
                }
                ls_AC3
            } catch (var20: Exception) {
                var20.toString()
            }
        }

        fun get_triv(USID: String, Key: String, ls_counter: String): String {
            return try {
                var ls_AC3 = ""
                var ls_usid_2 = ""
                var ls_key_2 = ""
                var ls_counter_2 = ""
                var i: Int
                i = 0
                while (i < 20) {
                    ls_usid_2 = ls_usid_2 + d_16_2String(USID.substring(i, i + 1))
                    ++i
                }
                i = 0
                while (i < 12) {
                    ls_key_2 = ls_key_2 + d_16_2String(Key.substring(i, i + 1))
                    ++i
                }
                i = 0
                while (i < 8) {
                    ls_counter_2 = ls_counter_2 + d_16_2String(ls_counter.substring(i, i + 1))
                    ++i
                }
                var s1 = ""
                var s2 = ""
                var s3 = ""
                var s_all = ""
                var z_all = ""
                val AC3_2 = ""
                s1 = ls_usid_2 + "0000000000000"
                s2 = ls_counter_2 + ls_key_2 + "0000"
                i = 1
                while (i <= 108) {
                    s3 = s3 + "0"
                    ++i
                }
                s3 = s3 + "111"
                s_all = s1 + s2 + s3
                var t11: String
                var t12: String
                var t13: String
                i = 1
                while (i <= 1152) {
                    t11 = xor(
                        xor(
                            xor(
                                s_all.substring(65, 66),
                                and(s_all.substring(90, 91), s_all.substring(91, 92))
                            ), s_all.substring(92, 93)
                        ), s_all.substring(170, 171)
                    )
                    t12 = xor(
                        xor(
                            xor(
                                s_all.substring(161, 162),
                                and(s_all.substring(174, 175), s_all.substring(175, 176))
                            ), s_all.substring(176, 177)
                        ), s_all.substring(263, 264)
                    )
                    t13 = xor(
                        xor(
                            xor(
                                s_all.substring(242, 243),
                                and(s_all.substring(285, 286), s_all.substring(286, 287))
                            ), s_all.substring(287, 288)
                        ), s_all.substring(68, 69)
                    )
                    s1 = t13 + s1.substring(0, 92)
                    s2 = t11 + s2.substring(0, 83)
                    s3 = t12 + s3.substring(0, 110)
                    s_all = s1 + s2 + s3
                    ++i
                }
                i = 0
                while (i < 80) {
                    t11 = xor(s_all.substring(65, 66), s_all.substring(92, 93))
                    t12 = xor(s_all.substring(161, 162), s_all.substring(176, 177))
                    t13 = xor(s_all.substring(242, 243), s_all.substring(287, 288))
                    val z = xor(xor(t11, t12), t13)
                    z_all = z_all + z
                    t11 = xor(
                        xor(
                            t11,
                            and(s_all.substring(90, 91), s_all.substring(91, 92))
                        ), s_all.substring(170, 171)
                    )
                    t12 = xor(
                        xor(
                            t12,
                            and(s_all.substring(174, 175), s_all.substring(175, 176))
                        ), s_all.substring(263, 264)
                    )
                    t13 = xor(
                        xor(
                            t13,
                            and(s_all.substring(285, 286), s_all.substring(286, 287))
                        ), s_all.substring(68, 69)
                    )
                    s1 = t13 + s1.substring(0, 92)
                    s2 = t11 + s2.substring(0, 83)
                    s3 = t12 + s3.substring(0, 110)
                    s_all = s1 + s2 + s3
                    ++i
                }
                i = 0
                while (i < 20) {
                    ls_AC3 = ls_AC3 + d_2_16String(AC3_2.substring(i * 4, i * 4 + 4))
                    ++i
                }
                ls_AC3
            } catch (var18: Exception) {
                var18.toString()
            }
        }

        fun xor(a: String, b: String): String {
            var c = ""
            if (a == "0" && b == "0") {
                c = "0"
            }
            if (a == "1" && b == "0") {
                c = "1"
            }
            if (a == "0" && b == "1") {
                c = "1"
            }
            if (a == "1" && b == "1") {
                c = "0"
            }
            return c
        }

        fun and(a: String, b: String): String {
            var c = ""
            if (a == "0" && b == "0") {
                c = "0"
            }
            if (a == "1" && b == "0") {
                c = "0"
            }
            if (a == "0" && b == "1") {
                c = "0"
            }
            if (a == "1" && b == "1") {
                c = "1"
            }
            return c
        }

        fun d_2_16String(a: String): String {
            var ls_16 = ""
            ls_16 = if (a == "0000") {
                "0"
            } else if (a == "0001") {
                "1"
            } else if (a == "0010") {
                "2"
            } else if (a == "0011") {
                "3"
            } else if (a == "0100") {
                "4"
            } else if (a == "0101") {
                "5"
            } else if (a == "0110") {
                "6"
            } else if (a == "0111") {
                "7"
            } else if (a == "1000") {
                "8"
            } else if (a == "1001") {
                "9"
            } else if (a == "1010") {
                "A"
            } else if (a == "1011") {
                "B"
            } else if (a == "1100") {
                "C"
            } else if (a == "1101") {
                "D"
            } else if (a == "1110") {
                "E"
            } else if (a == "1111") {
                "F"
            } else {
                "ERROR"
            }
            return ls_16
        }

        fun d_16_2String(a: String): String {
            var ls_2 = ""
            ls_2 = if (a == "0") {
                "0000"
            } else if (a == "1") {
                "0001"
            } else if (a == "2") {
                "0010"
            } else if (a == "3") {
                "0011"
            } else if (a == "4") {
                "0100"
            } else if (a == "5") {
                "0101"
            } else if (a == "6") {
                "0110"
            } else if (a == "7") {
                "0111"
            } else if (a == "8") {
                "1000"
            } else if (a == "9") {
                "1001"
            } else if (a == "A") {
                "1010"
            } else if (a == "B") {
                "1011"
            } else if (a == "C") {
                "1100"
            } else if (a == "D") {
                "1101"
            } else if (a == "E") {
                "1110"
            } else if (a == "F") {
                "1111"
            } else {
                "ERROR"
            }
            return ls_2
        }
    }
}