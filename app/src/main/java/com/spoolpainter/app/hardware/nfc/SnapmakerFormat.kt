package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.domain.models.OpenSpoolPayload
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

// User-provided Snapmaker salt constant (hardcoded)
const val SNAPMAKER_KEY_SALT = "536e61706d616b65725f71776572747975696f705b2c2e3b5d5f317132773365"

// ── Offsets (section*64 + block*16 + byte) ────────────────────────────────────
private const val VENDOR_POS       = 0 * 64 + 1 * 16 + 0;  private const val VENDOR_LEN       = 16
private const val MAIN_TYPE_POS    = 1 * 64 + 0 * 16 + 2
private const val SUB_TYPE_POS     = 1 * 64 + 0 * 16 + 4
private const val COLOR_NUMS_POS   = 1 * 64 + 0 * 16 + 8
private const val ALPHA_POS        = 1 * 64 + 0 * 16 + 9
private const val RGB1_POS         = 1 * 64 + 1 * 16 + 0
private const val RGB2_POS         = 1 * 64 + 1 * 16 + 3
private const val RGB3_POS         = 1 * 64 + 1 * 16 + 6
private const val RGB4_POS         = 1 * 64 + 1 * 16 + 9
private const val RGB5_POS         = 1 * 64 + 1 * 16 + 12
private const val SKU_POS          = 1 * 64 + 2 * 16 + 0
private const val DIAMETER_POS     = 2 * 64 + 0 * 16 + 0
private const val WEIGHT_POS       = 2 * 64 + 0 * 16 + 2
private const val LENGTH_POS       = 2 * 64 + 0 * 16 + 4
private const val DRY_TEMP_POS     = 2 * 64 + 1 * 16 + 0
private const val DRY_TIME_POS     = 2 * 64 + 1 * 16 + 2
private const val HOT_MAX_POS      = 2 * 64 + 1 * 16 + 4
private const val HOT_MIN_POS      = 2 * 64 + 1 * 16 + 6
private const val BED_TEMP_POS     = 2 * 64 + 1 * 16 + 10
private const val MFG_DATE_POS     = 2 * 64 + 2 * 16 + 0;  private const val MFG_DATE_LEN     = 8
private const val RSA_VER_POS      = 2 * 64 + 2 * 16 + 8

private val MAIN_TYPES = mapOf(1 to "PLA", 2 to "PETG", 3 to "ABS", 4 to "TPU", 5 to "PVA")
private val SUB_TYPES  = mapOf(
    1 to "Basic", 2 to "Matte", 3 to "SnapSpeed", 4 to "Silk",
    5 to "Support", 6 to "HF", 7 to "95A", 8 to "95A HF"
)

// RSA-2048 public keys (PKCS#1 PEM), indexed by version byte on tag
private val RSA_PEM_KEYS = mapOf(
    0 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8oEF7YuKO863TbUxnrvY
H1JFrvCnMapm8Ho952KlfNWbf6IEDMlX6QJpBuvUkrkjWpLJJQurIWL3KFeLUhCh
POrYdiGrdsUlp4YO037iLSlgmzo1dUdgbawAcGox1PvR/Naw5ADibubO2rN49WQR
+BkxxigvoWHSFetaoMCswQ5B/niq3byhzktgmWOcv71F4yFwcxivF8R+s0gSBL4i
/1zNeSUZkbvP4/T0B08i3D+e6fl9xpCnINZ3P9OWcx+p3SB2o4TdmAeKV4hkT9n7
o+/OWr92fx6qbiNKJr04oMhrRsFK6w7hitp2n8RGS64w9lhtplnBgxtbgxAYyUnp
qwIDAQAB""",
    1 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA8nbtQNABbc5PkyzI0A5m
VH/E8y23Wld0iykvTOoBYJOrPwJDmXsnSyyX84Nv6voSr8FYv3Fb2SqSdOgQLFqp
BXvntXew8rPpq5Ll8gSzLRxE1VmEOVtZWCTJ4Wxwwi79rrFmpa/nAtUeYZIGiiud
w2MzCHXW5G3c1FWhQ0C8vUUMfBQXmGnoHGsul6R8xld6CDCWY8ia/FvfR+KCtMRn
VYyYguYsq4rODWJHiFCOef4FZconUR3RTh0ojvq78CsHk94goxidWzZoKcVnvWhh
bOixTjU37W4JDECEOui3ObMMvJkzxkZo1irlAH7jTiPqhP94U/JbRDpBlHOOn67b
GQIDAQAB""",
    2 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxZQPYewwMFaPlcEHq+SH
QS1C1NhVmAaY56qxLyHJ4aNc2iWdCx4/9ZKY4CL6xkeCD88Zndv/xzImplRdoAzo
whD47Vm4iuq8+NqHUI8na6ISd+MZ/O6/eo/ggaEZBX8lR+Yf0qfWtntsI9flUOoJ
mq1IXvNXqOxflUmPyffT40QSkAN4Rr3scB3ozlxuJZehWM/lUmZ1H5PQDwAqsM0T
Rj6ChzVmUbSvwEvbDTwpXkpMA0C5//OW0T//IKDEBYxEl928vYbraLRDRIetgdaD
o+77+ztfOv4AyP/ipikprHwIWi7yga5KUXq/XpNPy6cPISZD+/LBUJBxLELspREP
rQIDAQAB""",
    3 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvK8cJyeFeTkFgkSLCCAg
EgR9KAvIHmvK8CRdtn+W6PiIbN04MFIg8jiYW/3fq+AcBFFMo+HtR2gym8JNVx2I
RDI4WdfbR/0gaIHjOQ41OwlXmqqSkDsFmjxVI6bDRZYpHkOfkC+9Vi1Aii4l/Yq9
O7s+2j4zP9GoUWWJPb3mW07Vu+EnHB/XIuaoDJVQAS+ov3xTotCeKdcdgySnNP5g
kOvWUvWtwNQldCRcQ0eo3j5RO+4J4IRK2J8q7BrdV/gbJUE/BBPIOuURPLzNJJO3
wgx4PEwlb5uYEUL35ARL7NzL8ZOxebzs5H4tXuWrBhALw6O33Tfg3TmTmwR2JUpv
7QIDAQAB""",
    4 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvafhk7Bdb3F+5B9w7YXv
chrNzl09QkZc27NLxL0ViRitGQhX9KC/xVg+XkBGI8XfioAwYkJ3jYgwmci5gJOL
ofPyNXcFtvtzq2NZNuDZY26krrXLORhS1o8ue92RB2gM92Rc2heWVrsvLycNl2Qz
OUjUEGmWpSMo98xIsgkTZJ4aYxWVN86yqknOcHVpTmcr5SBRB90K9hTRtsaMD97O
FYVc7AA/TGwqFJOnXXzWczWtg7kUY2vqCHwsvKs3G/EIFKOIe1n37V94OcxHTySC
co9Kc6Y0bGFIwIruinH1WkFVt6TAzo+0ZdZy5Sq493AG9y1RZ5nYj5qUmc1PMmrD
gwIDAQAB""",
    5 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxWdxd7qeouSFbZ2Sldv3
apDrgAupOYiDRkO85C+qkZaezOzqW0EsOV0x7nG/smw++TRfHyGIK4gXCdg1JfNR
WYjqckRdnLYMzGdDk24VV5Bbrsgska0v0Oy1ucz3CYu+F22ais00OqK0MY0B96MI
/B/0pRSTAIyxvC6LjhHy8DYyPdqNF9EMikKfAfcn7ytsH1PoSSGVtrZqyNe5OLrW
yAw+FQsTg/VFJcYxPTQJ1ymwQmDCdKgApe3PVajyYswoIA7R0S8ujau0aAFEO3dU
GDEwjOnaHfwFlg3OKMFJTxc2sl/WEB8xtWuKl0Guf0VnzWJ6noxqf/DiaN1fuHG0
AwIDAQAB""",
    6 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqF+YJNHLHC6c25oTDgNg
liahUxWBPSkgght1/gJu5vBRDKWEn6i/RuKAFdTOsH+Hlvr5qWms7bBUHx78UMF+
FF1Nq9tb4jhFuqq4HWsBBjNnU6O0JhFTjKJU2nudmphXlpdLQfcKSIYMQe795GHL
izh8WsNTcTHNNBkjhi7y4c4RUqnJso0L6vrf0B3EB/9DDUJitrwfw+1/OrKOEVEP
624sEa802cHfb+BG9zKBXjFwzYCYF9gWey9yeA3UA7EYmPpqA1lqNv8m0r7YjZ4n
uGBDjs+AXaGtdqrW3IUtkUF2vWwNSRncbcXi3mNfzslrtPhsDVAFki4vDSw7yNht
2wIDAQAB""",
    7 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuKWRCTTgxPltfflWHdhu
2ITxWC/LTEl7OtatNWFhMFQZF2J5SN/45bjH6xIPTcDglTSl2/UMC1D/ugiq+j0z
dGSdE7xn3ZSzLTMCwgRkvXmd8aQgafBYbB7E6oAgus+6lRXZPwnMfZAe0yaJNHyt
1Wd8ZUlRY7BHSPPtmG1liVEzxoTb6urB6mK49r24+oC7xa65q5NSdlZWSTeaK4Xt
DVVDiwe+uubNTm59KnVAKgBMNd3qN942pH6fo/dBz++BzJVEG/qJewHUTGZAeIl+
CgqhSEbmEIgolsDgaKY99ZWa2FWJdo+ohYhmjc92TyB9kWw6yIwez+tlRUkssLGt
SwIDAQAB""",
    8 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt7XOTs6P2xB8v8/xWVdR
wVefphRDXSuv74RObtr0pwLTc7BytkcDw8r60BNPv9hGDpW2S1szxqS8x4EaOHP7
81qNpIUULlUdXxty1RvpSdfRb044kpwl7A/s4OEakkyJZF1ed+Qte1FqOFDDIZ+l
g+Co8FjOwWixoSyIlR22mEP7r6Y98GL5tnSohkVoGAgEipswWb6549mssjZmES+J
hB0axY6Dl/LlDYxN6jjUZwSIo7bw0GXGm9ScW2qTVaT1m2A9etpD6OIG+iQVLQqP
whVBs5q0o/EM4nBN88RBsF2OmfkcZPJ2NdX6o3qx+pCZ9NDgkHjGDZdnGEnM5Lu2
dwIDAQAB""",
    9 to """MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAz/d5C5FpqlcF7NbUEvBN
fiDJWH0BF63PEwHPiX+cS6l+q4NqqYI167u1pGkZGJV1njgGYFTM08x2KO7/bk6o
CWcGuKWNM8Tp1+tv3XioNGVCnIpHmdUx5F9qcXlPtDx74wQk/+JZLQ/sLnLvHcV3
YTaz55fpyzVUHkgXusdVynSyAt3ywWWQRcjp3sspGa/udC0j6LCvrzqLACv3gMGA
Id0b6REzjSn03UzkwBIwSb8DszieeNhaCOK4M/TxPFNyrhQRYcAvhiZJu+tylqJs
VP+gaWFvElFeFkxcHvYXHdJPlJLjYeT51hm/pdll26yYLhpeBa0inHwSqv4D3jFZ
PQIDAQAB"""
)

// ── HKDF helpers ─────────────────────────────────────────────────────────────
private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

private fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

fun snapmakerDeriveKeys(uid4: ByteArray, saltHex: String): Pair<List<ByteArray>, List<ByteArray>> {
    val salt = hexToBytes(saltHex)
    val saltA = salt.copyOfRange(0, 25)
    val saltB = salt
    return Pair(hkdfKeys(uid4, saltA, "a"), hkdfKeys(uid4, saltB, "b"))
}

private fun hkdfKeys(ikm: ByteArray, salt: ByteArray, keyType: String): List<ByteArray> {
    val s = if (salt.isNotEmpty() && salt.last() == 0.toByte()) salt.copyOfRange(0, salt.size - 1) else salt
    val prk = hmacSha256(s, ikm)
    return List(16) { i ->
        val info = "key_${keyType}_$i".toByteArray(Charsets.UTF_8)
        val okm = mutableListOf<Byte>()
        var t = byteArrayOf()
        var counter = 1
        while (okm.size < 6) {
            t = hmacSha256(prk, t + info + counter.toByte())
            okm.addAll(t.toList())
            counter++
        }
        okm.take(6).toByteArray()
    }
}

// ── RSA verification ─────────────────────────────────────────────────────────
private fun parsePkcs1PublicKey(b64: String): PublicKey {
    val der = Base64.getDecoder().decode(b64.replace("\n", "").replace("\r", ""))
    
    fun readTlv(d: ByteArray, offset: Int): Pair<ByteArray, Int> {
        var off = offset + 1
        val lenByte = d[off].toInt() and 0xFF
        val len: Int
        if (lenByte and 0x80 == 0) {
            len = lenByte; off++
        } else {
            val numBytes = lenByte and 0x7F; off++
            var l = 0
            repeat(numBytes) { l = (l shl 8) or (d[off + it].toInt() and 0xFF) }
            off += numBytes; len = l
        }
        return Pair(d.copyOfRange(off, off + len), off + len)
    }

    fun readBigInt(bytes: ByteArray): java.math.BigInteger {
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
        return java.math.BigInteger(1, bytes.copyOfRange(start, bytes.size))
    }

    val (seqBytes, _) = readTlv(der, 0)
    val pkcs1: ByteArray = if (seqBytes[0] == 0x30.toByte()) {
        val (_, afterAlg) = readTlv(seqBytes, 0)
        val (bitStrBytes, _) = readTlv(seqBytes, afterAlg)
        val inner = bitStrBytes.copyOfRange(1, bitStrBytes.size)
        val (innerSeq, _) = readTlv(inner, 0)
        innerSeq
    } else {
        seqBytes
    }

    val (modBytes, nextOff) = readTlv(pkcs1, 0)
    val (expBytes, _) = readTlv(pkcs1, nextOff)
    val keySpec = RSAPublicKeySpec(readBigInt(modBytes), readBigInt(expBytes))
    return KeyFactory.getInstance("RSA").generatePublic(keySpec)
}

private fun verifySignature(data: ByteArray, keysA: List<ByteArray>?, keysB: List<ByteArray>?): Boolean {
    val rsaVer = (data[RSA_VER_POS].toInt() and 0xFF) or ((data[RSA_VER_POS + 1].toInt() and 0xFF) shl 8)
    val pemBody = RSA_PEM_KEYS[rsaVer] ?: return false

    val sigBytes = ByteArray(256)
    var written = 0
    for (i in 0 until 6) {
        val src = (10 + i) * 64
        val toCopy = minOf(48, 256 - written)
        if (toCopy <= 0) break
        data.copyInto(sigBytes, written, src, src + toCopy)
        written += toCopy
    }

    val msg = data.copyOfRange(0, 640)
    if (keysA != null && keysB != null) {
        for (s in 0 until 10) {
            val trailerOff = s * 64 + 48
            keysA[s].copyInto(msg, trailerOff)
            keysB[s].copyInto(msg, trailerOff + 10)
        }
    }

    return try {
        val pubKey = parsePkcs1PublicKey(pemBody)
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initVerify(pubKey)
        signer.update(msg)
        signer.verify(sigBytes)
    } catch (_: Exception) { false }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun readU16LE(data: ByteArray, pos: Int): Int =
    (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)

private fun readU32LE(data: ByteArray, pos: Int): Int =
    (data[pos].toInt() and 0xFF) or
    ((data[pos + 1].toInt() and 0xFF) shl 8) or
    ((data[pos + 2].toInt() and 0xFF) shl 16) or
    ((data[pos + 3].toInt() and 0xFF) shl 24)

private fun readAscii(data: ByteArray, pos: Int, len: Int): String {
    val slice = data.copyOfRange(pos, pos + len)
    val end = slice.indexOfFirst { it == 0.toByte() }
    return String(if (end >= 0) slice.copyOfRange(0, end) else slice, Charsets.US_ASCII)
}

// ── Parser entry point ────────────────────────────────────────────────────────
fun parseSnapmakerTag(
    data: ByteArray,
    keysA: List<ByteArray>? = null,
    keysB: List<ByteArray>? = null
): OpenSpoolPayload? {
    if (data.size != 1024) return null
    if (!verifySignature(data, keysA, keysB)) return null

    val mainTypeCode = readU16LE(data, MAIN_TYPE_POS)
    val subTypeCode  = readU16LE(data, SUB_TYPE_POS)
    val mainType = MAIN_TYPES[mainTypeCode] ?: return null
    val subType  = SUB_TYPES[subTypeCode]  ?: return null

    val alpha     = 0xFF - (data[ALPHA_POS].toInt() and 0xFF)
    val colorNums = (data[COLOR_NUMS_POS].toInt() and 0xFF).coerceIn(0, 5)

    fun readRgb(pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 16) or
        ((data[pos + 1].toInt() and 0xFF) shl 8) or
        (data[pos + 2].toInt() and 0xFF)

    val rgbPositions = listOf(RGB1_POS, RGB2_POS, RGB3_POS, RGB4_POS, RGB5_POS)
    val colors = (0 until colorNums).map { i -> (alpha shl 24) or readRgb(rgbPositions[i]) }.toMutableList()
    if (colors.isEmpty()) colors.add((alpha shl 24) or readRgb(RGB1_POS))

    val vendor   = readAscii(data, VENDOR_POS, VENDOR_LEN)
    val sku      = readU32LE(data, SKU_POS)
    val length   = readU16LE(data, LENGTH_POS)
    val mfgDate  = readAscii(data, MFG_DATE_POS, MFG_DATE_LEN)
    val diameter = readU16LE(data, DIAMETER_POS) / 100.0
    val weight   = readU16LE(data, WEIGHT_POS)
    val dryTemp  = readU16LE(data, DRY_TEMP_POS)
    val dryTime  = readU16LE(data, DRY_TIME_POS)
    val hotMax   = readU16LE(data, HOT_MAX_POS)
    val hotMin   = readU16LE(data, HOT_MIN_POS)
    val bed      = readU16LE(data, BED_TEMP_POS)

    val colorHexStr = "%06X".format(colors.first() and 0xFFFFFF)

    return OpenSpoolPayload(
        protocol = "openspool",
        version = "1.0",
        type = mainType,
        colorHex = colorHexStr,
        brand = vendor.ifBlank { "Snapmaker" },
        minTemp = hotMin.toString(),
        maxTemp = hotMax.toString(),
        bedMinTemp = bed.toString(),
        bedMaxTemp = bed.toString(),
        subtype = subType,
        spoolId = null
    )
}
