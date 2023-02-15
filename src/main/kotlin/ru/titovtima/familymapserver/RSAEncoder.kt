package ru.titovtima.familymapserver

import java.math.BigInteger

class RSAEncoder {
    companion object {
        val n = BigInteger("704523235194356558888787777389344656284323185922432581704826547525191826229")
        val e = BigInteger("117295751437621283408882524061993041471")

        fun powMod(a: BigInteger, p: BigInteger, mod: BigInteger = n): BigInteger {
            if (p == BigInteger.ZERO) return BigInteger.ONE
            if (p == BigInteger.ONE) return a

            val a2 = powMod(a, p / BigInteger.TWO, mod)
            return if (p.mod(BigInteger.TWO) == BigInteger.ZERO)
                a2 * a2 % mod
            else
                a2 * a2 * a % mod
        }

        fun encode(string: String): BigInteger {
            var strToBigInt = BigInteger.ZERO
            string.forEach { strToBigInt = (strToBigInt * BigInteger("256") + BigInteger(it.code.toString())) % n }
            return powMod(strToBigInt, e)
        }
    }
}